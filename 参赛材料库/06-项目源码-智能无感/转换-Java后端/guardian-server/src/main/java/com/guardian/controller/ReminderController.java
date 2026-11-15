package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.common.ViewUtil;
import com.guardian.entity.MedicineReminder;
import com.guardian.entity.User;
import com.guardian.mapper.AlertMapper;
import com.guardian.mapper.MedicineReminderMapper;
import com.guardian.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 提醒管理接口
 *
 * 路由：
 *   GET    /api/reminder/configs/{userId}   提醒配置列表
 *   POST   /api/reminder/configs/create     新建提醒
 *   POST   /api/reminder/configs/update     更新提醒
 *   DELETE /api/reminder/configs/delete     删除提醒
 *   POST   /api/reminder/trigger            触发提醒
 *   GET    /api/reminder/history/{userId}   提醒历史
 *   GET    /api/reminder/log                提醒日志查询
 *   POST   /api/reminder/log                写提醒日志
 *   GET    /api/reminder/settings           提醒设置
 *   POST   /api/reminder/settings           同步提醒设置
 *   GET    /api/reminder/today              今日待办提醒
 *   POST   /api/reminder/toggle             开关某条提醒
 *   GET    /api/reminder/checkin-reminder   打卡提醒配置
 *   POST   /api/reminder/checkin-reminder   设置打卡提醒
 *
 * 提醒类型：
 *   checkin  打卡提醒（复用 medicine_reminders 的 time 语义，medicine_name='__checkin__'）
 *   medicine 用药提醒（落在 medicine_reminders 表）
 *   custom   自定义提醒（落在 medicine_reminders 表，用 notes 标记）
 *
 * ★ 数据库没有独立的 reminders 表，统一复用 medicine_reminders，
 *   通过 medicine_name 前缀区分 checkin / custom，避免改动建表脚本。
 *   提醒设置（免打扰时段、开关）存 Redis，随用户维度隔离。
 */
@Slf4j
@RestController
@RequestMapping("/reminder")
@RequiredArgsConstructor
public class ReminderController {

    private final MedicineReminderMapper reminderMapper;
    private final AlertMapper alertMapper;
    private final AuthService authService;
    private final StringRedisTemplate redis;

    /** 内部约定的前缀：打卡提醒 */
    private static final String PREFIX_CHECKIN = "__checkin__";
    /** 内部约定的前缀：自定义提醒 */
    private static final String PREFIX_CUSTOM = "__custom__";
    /** 提醒设置缓存 key */
    private static final String SETTINGS_KEY = "guardian:reminder:settings:";
    /** 提醒日志缓存 key */
    private static final String LOG_KEY = "guardian:reminder:log:";

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    // ========================================================================
    // ① 提醒配置 CRUD
    // ========================================================================

    /** 提醒配置列表 */
    @GetMapping("/configs/{userId}")
    public ApiResponse<Map<String, Object>> configs(HttpServletRequest req,
                                                    @PathVariable String userId,
                                                    @RequestParam(required = false) Boolean isActive,
                                                    @RequestParam(required = false) String type) {
        authService.requireUser(token(req));

        List<MedicineReminder> rows = reminderMapper.selectByUser(userId, isActive);

        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Integer> typeCount = new LinkedHashMap<>();
        typeCount.put("checkin", 0);
        typeCount.put("medicine", 0);
        typeCount.put("custom", 0);

        for (MedicineReminder r : rows) {
            Map<String, Object> v = toReminderView(r);
            String t = String.valueOf(v.get("type"));
            // 类型过滤
            if (type != null && !type.isBlank() && !type.equals(t)) continue;

            typeCount.merge(t, 1, Integer::sum);
            list.add(v);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        data.put("userId", userId);
        data.put("typeCount", typeCount);
        data.put("generatedAt", LocalDateTime.now().toString());
        return ApiResponse.ok(data);
    }

    /**
     * 新建提醒
     *
     * 请求体：
     * {
     *   "userId": "...",           // 可选，默认自己
     *   "type": "medicine",        // checkin / medicine / custom
     *   "medicineName": "降压药",   // custom 时作为标题
     *   "dosage": "1片",
     *   "frequency": "twice",      // daily / twice / three_times / weekly
     *   "times": ["08:00","20:00"],
     *   "startDate": "2026-01-01",
     *   "endDate": "2026-02-01",
     *   "notes": "...",
     *   "enabled": true
     * }
     */
    @PostMapping("/configs/create")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> create(HttpServletRequest req,
                                                   @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) body = new LinkedHashMap<>();

        String userId = ViewUtil.toStr(body.get("userId"), me.getId());
        String type = normalizeType(ViewUtil.toStr(body.get("type"), "medicine"));

        // 标题：打卡提醒固定语义
        String name;
        if ("checkin".equals(type)) {
            name = PREFIX_CHECKIN + ViewUtil.toStr(body.get("medicineName"), "作息打卡提醒");
        } else if ("custom".equals(type)) {
            name = PREFIX_CUSTOM + ViewUtil.toStr(body.get("medicineName"),
                    ViewUtil.toStr(body.get("title"), "自定义提醒"));
        } else {
            name = ViewUtil.toStr(body.get("medicineName"));
            if (name == null) {
                throw BizException.badRequest("请填写药品名称");
            }
        }

        List<String> times = ViewUtil.toStrList(body.get("times"));
        if (times.isEmpty()) {
            throw BizException.badRequest("请至少提供一个提醒时间（times）");
        }
        for (String t : times) {
            if (!isValidTime(t)) {
                throw BizException.badRequest("时间格式不正确（应为 HH:mm）：" + t);
            }
        }

        MedicineReminder r = new MedicineReminder();
        r.setId(CommonUtil.uuid());
        r.setUserId(userId);
        r.setMedicineName(name);
        r.setDosage(ViewUtil.toStr(body.get("dosage"), defaultDosage(type)));
        r.setFrequency(ViewUtil.toStr(body.get("frequency"), defaultFrequency(times.size())));
        r.setTimes(toJsonArray(times));
        r.setStartDate(ViewUtil.toDate(body.get("startDate")) == null
                ? LocalDate.now() : ViewUtil.toDate(body.get("startDate")));

        LocalDate end = ViewUtil.toDate(body.get("endDate"));
        if (end != null && end.isBefore(r.getStartDate())) {
            throw BizException.badRequest("结束日期不能早于开始日期");
        }
        r.setEndDate(end);
        r.setNotes(ViewUtil.toStr(body.get("notes")));
        r.setIsActive(ViewUtil.toBool(body.get("enabled"), true));

        reminderMapper.insert(r);
        log.info("用户 {} 新建{}提醒 {}（{} 个时间点）", userId, typeLabel(type), r.getId(), times.size());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", r.getId());
        data.put("type", type);
        data.put("reminder", toReminderView(r));
        return ApiResponse.ok("提醒已创建", data);
    }

    /**
     * 更新提醒
     *
     * 请求体：{ "id": "...", "times": [...], "dosage": "...", "enabled": false, ... }
     */
    @PostMapping("/configs/update")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> update(HttpServletRequest req,
                                                   @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) body = new LinkedHashMap<>();

        String id = ViewUtil.toStr(body.get("id"));
        if (id == null) {
            throw BizException.badRequest("缺少提醒 ID");
        }

        MedicineReminder exist = reminderMapper.selectById(id);
        if (exist == null) {
            throw BizException.notFound("提醒不存在");
        }
        assertOwner(exist, me);

        MedicineReminder patch = new MedicineReminder();
        patch.setId(id);

        // 名称：保持原有的类型前缀，只替换标题部分
        String title = ViewUtil.toStr(body.get("medicineName"));
        if (title == null) title = ViewUtil.toStr(body.get("title"));
        if (title != null) {
            String prefix = typePrefix(exist.getMedicineName());
            patch.setMedicineName(prefix + title);
        }

        if (body.containsKey("dosage")) patch.setDosage(ViewUtil.toStr(body.get("dosage")));

        List<String> times = ViewUtil.toStrList(body.get("times"));
        if (!times.isEmpty()) {
            for (String t : times) {
                if (!isValidTime(t)) {
                    throw BizException.badRequest("时间格式不正确（应为 HH:mm）：" + t);
                }
            }
            patch.setTimes(toJsonArray(times));
            if (!body.containsKey("frequency")) {
                patch.setFrequency(defaultFrequency(times.size()));
            }
        }

        if (body.containsKey("frequency")) patch.setFrequency(ViewUtil.toStr(body.get("frequency")));
        if (body.containsKey("startDate")) patch.setStartDate(ViewUtil.toDate(body.get("startDate")));
        if (body.containsKey("endDate")) patch.setEndDate(ViewUtil.toDate(body.get("endDate")));
        if (body.containsKey("notes")) patch.setNotes(ViewUtil.toStr(body.get("notes")));

        Boolean enabled = ViewUtil.toBool(body.get("enabled"));
        if (enabled == null) enabled = ViewUtil.toBool(body.get("isActive"));
        if (enabled != null) patch.setIsActive(enabled);

        reminderMapper.update(patch);

        MedicineReminder updated = reminderMapper.selectById(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("reminder", toReminderView(updated));
        return ApiResponse.ok("提醒已更新", data);
    }

    /**
     * 删除提醒
     *
     * DELETE 带 body：{ "id": "..." }
     */
    @DeleteMapping("/configs/delete")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> delete(HttpServletRequest req,
                                                   @RequestBody(required = false) Map<String, Object> body) {
        User me = authService.requireUser(token(req));

        String id = body == null ? null : ViewUtil.toStr(body.get("id"));
        if (id == null) {
            throw BizException.badRequest("缺少提醒 ID");
        }

        MedicineReminder exist = reminderMapper.selectById(id);
        if (exist == null) {
            throw BizException.notFound("提醒不存在");
        }
        assertOwner(exist, me);

        // 软删除：置为停用，保留历史
        reminderMapper.deleteById(id);
        log.info("提醒 {} 已停用（用户 {}）", id, me.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("id", id);
        data.put("message", "提醒已删除");
        return ApiResponse.ok("提醒已删除", data);
    }

    /**
     * 开关某条提醒
     *
     * 请求体：{ "id": "...", "enabled": true }（不传 enabled 则取反）
     */
    @PostMapping("/toggle")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> toggle(HttpServletRequest req,
                                                   @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));

        String id = body == null ? null : ViewUtil.toStr(body.get("id"));
        if (id == null) {
            throw BizException.badRequest("缺少提醒 ID");
        }

        MedicineReminder exist = reminderMapper.selectById(id);
        if (exist == null) {
            throw BizException.notFound("提醒不存在");
        }
        assertOwner(exist, me);

        Boolean target = ViewUtil.toBool(body.get("enabled"));
        boolean next = target != null ? target : !Boolean.TRUE.equals(exist.getIsActive());

        MedicineReminder patch = new MedicineReminder();
        patch.setId(id);
        patch.setIsActive(next);
        reminderMapper.update(patch);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("enabled", next);
        data.put("type", typeOf(exist.getMedicineName()));
        return ApiResponse.ok(next ? "提醒已开启" : "提醒已关闭", data);
    }

    // ========================================================================
    // ② 今日待办 / 触发
    // ========================================================================

    /**
     * 今日待办提醒
     *
     * GET /api/reminder/today
     *
     * 汇总今天所有启用中的提醒时间点，按时刻排序，并标注是否已过点。
     */
    @GetMapping("/today")
    public ApiResponse<Map<String, Object>> today(HttpServletRequest req,
                                                  @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));
        String targetId = blankToNull(userId) == null ? me.getId() : userId;

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        List<MedicineReminder> rows = reminderMapper.selectByUser(targetId, true);

        List<Map<String, Object>> items = new ArrayList<>();
        for (MedicineReminder r : rows) {
            // 生效日期区间判断
            if (r.getStartDate() != null && today.isBefore(r.getStartDate())) continue;
            if (r.getEndDate() != null && today.isAfter(r.getEndDate())) continue;

            String type = typeOf(r.getMedicineName());
            for (String t : parseJsonArray(r.getTimes())) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("reminderId", r.getId());
                m.put("type", type);
                m.put("typeText", typeLabel(type));
                m.put("title", displayName(r.getMedicineName()));
                m.put("dosage", r.getDosage());
                m.put("time", t);
                m.put("date", today.toString());
                m.put("passed", isBefore(t, now));
                m.put("notes", r.getNotes());
                items.add(m);
            }
        }

        // 按时间正序
        items.sort(Comparator.comparing(m -> String.valueOf(m.get("time"))));

        long pending = items.stream().filter(m -> Boolean.FALSE.equals(m.get("passed"))).count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", today.toString());
        data.put("now", now.format(ViewUtil.TIME_FMT));
        data.put("list", items);
        data.put("total", items.size());
        data.put("pending", pending);
        data.put("done", items.size() - pending);
        return ApiResponse.ok(data);
    }

    /**
     * 触发提醒
     *
     * 请求体：{ "reminderId": "...", "time": "08:00", "userId": "..." }
     *
     * ★ 会写一条提醒日志，并在提醒时间已过 30 分钟仍未确认时产生 low 级告警。
     */
    @PostMapping("/trigger")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> trigger(HttpServletRequest req,
                                                    @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) body = new LinkedHashMap<>();

        String targetId = ViewUtil.toStr(body.get("userId"), me.getId());
        String reminderId = ViewUtil.toStr(body.get("reminderId"));
        String time = ViewUtil.toStr(body.get("time"));

        String title = "提醒";
        String type = "custom";

        if (reminderId != null) {
            MedicineReminder r = reminderMapper.selectById(reminderId);
            if (r != null) {
                title = displayName(r.getMedicineName());
                type = typeOf(r.getMedicineName());
                if (time == null) {
                    List<String> times = parseJsonArray(r.getTimes());
                    time = times.isEmpty() ? null : times.get(0);
                }
            }
        }

        String message = String.format("【%s】%s %s，请及时处理",
                typeLabel(type), title, time == null ? "到时间了" : time);

        // 写提醒日志
        appendLog(targetId, ViewUtil.map(
                "reminderId", reminderId,
                "type", type,
                "title", title,
                "time", time,
                "event", "trigger",
                "message", message,
                "at", LocalDateTime.now().toString()
        ));

        log.info("提醒触发：用户 {}，{}", targetId, message);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("triggered", true);
        data.put("reminderId", reminderId);
        data.put("type", type);
        data.put("title", title);
        data.put("time", time);
        data.put("message", message);
        data.put("at", LocalDateTime.now().toString());
        return ApiResponse.ok(message, data);
    }

    // ========================================================================
    // ③ 日志 / 历史
    // ========================================================================

    /**
     * 提醒历史
     *
     * GET /api/reminder/history/{userId}
     */
    @GetMapping("/history/{userId}")
    public ApiResponse<Map<String, Object>> history(HttpServletRequest req,
                                                    @PathVariable String userId,
                                                    @RequestParam(defaultValue = "7") int days,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        authService.requireUser(token(req));

        int p = ViewUtil.clamp(page, 1, 100000);
        int size = ViewUtil.clamp(pageSize, 1, 100);
        int d = ViewUtil.clamp(days, 1, 365);

        List<Map<String, Object>> all = readLogs(userId);

        // 只保留窗口内的记录
        LocalDateTime from = LocalDateTime.now().minusDays(d);
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> m : all) {
            LocalDateTime at = ViewUtil.toDateTime(m.get("at"));
            if (at == null || !at.isBefore(from)) {
                filtered.add(m);
            }
        }

        int total = filtered.size();
        int offset = Math.min((p - 1) * size, total);
        int end = Math.min(offset + size, total);
        List<Map<String, Object>> pageList = filtered.subList(offset, end);

        Map<String, Object> data = ViewUtil.page(pageList, total, p, size);
        data.put("days", d);
        data.put("userId", userId);
        return ApiResponse.ok(data);
    }

    /** 提醒日志查询 */
    @GetMapping("/log")
    public ApiResponse<Map<String, Object>> getLog(HttpServletRequest req,
                                                   @RequestParam(required = false) String userId,
                                                   @RequestParam(defaultValue = "50") int limit) {
        User me = authService.requireUser(token(req));
        String targetId = blankToNull(userId) == null ? me.getId() : userId;

        int lim = ViewUtil.clamp(limit, 1, 500);
        List<Map<String, Object>> logs = readLogs(targetId);

        int end = Math.min(lim, logs.size());
        List<Map<String, Object>> list = new ArrayList<>(logs.subList(0, end));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", logs.size());
        data.put("returned", list.size());
        return ApiResponse.ok(data);
    }

    /**
     * 写提醒日志
     *
     * 请求体：{ "userId": "...", "event": "taken|missed|snooze|trigger", "reminderId": "...", "message": "..." }
     */
    @PostMapping("/log")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> logReminder(HttpServletRequest req,
                                                        @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) body = new LinkedHashMap<>();

        String targetId = ViewUtil.toStr(body.get("userId"), me.getId());
        String event = ViewUtil.toStr(body.get("event"), "unknown");
        String reminderId = ViewUtil.toStr(body.get("reminderId"));
        String title = ViewUtil.toStr(body.get("title"));
        String message = ViewUtil.toStr(body.get("message"));

        // 关联提醒时补全标题
        String type = "custom";
        if (reminderId != null) {
            MedicineReminder r = reminderMapper.selectById(reminderId);
            if (r != null) {
                if (title == null) title = displayName(r.getMedicineName());
                type = typeOf(r.getMedicineName());
            }
        }

        Map<String, Object> entry = ViewUtil.map(
                "reminderId", reminderId,
                "type", type,
                "title", title,
                "event", event,
                "message", message == null ? eventLabel(event) : message,
                "at", LocalDateTime.now().toString()
        );

        appendLog(targetId, entry);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("entry", entry);
        return ApiResponse.ok("日志已记录", data);
    }

    // ========================================================================
    // ④ 设置
    // ========================================================================

    /**
     * 提醒设置
     *
     * GET /api/reminder/settings?userId=...
     *
     * 返回：全局开关、免打扰时段、提前量、声音/震动等。
     */
    @GetMapping("/settings")
    public ApiResponse<Map<String, Object>> getSettings(HttpServletRequest req,
                                                        @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));
        String targetId = blankToNull(userId) == null ? me.getId() : userId;

        Map<String, Object> data = readSettings(targetId);
        data.put("userId", targetId);
        return ApiResponse.ok(data);
    }

    /**
     * 同步提醒设置
     *
     * 请求体（字段均可选，只更新传入的）：
     * {
     *   "enabled": true,
     *   "sound": true,
     *   "vibrate": true,
     *   "advanceMinutes": 5,
     *   "quietHoursStart": "22:00",
     *   "quietHoursEnd": "07:00",
     *   "checkinReminderEnabled": true
     * }
     */
    @PostMapping("/settings")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> syncSettings(HttpServletRequest req,
                                                         @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) body = new LinkedHashMap<>();

        String targetId = ViewUtil.toStr(body.get("userId"), me.getId());
        Map<String, Object> cur = readSettings(targetId);

        if (body.containsKey("enabled")) cur.put("enabled", ViewUtil.toBool(body.get("enabled"), true));
        if (body.containsKey("sound")) cur.put("sound", ViewUtil.toBool(body.get("sound"), true));
        if (body.containsKey("vibrate")) cur.put("vibrate", ViewUtil.toBool(body.get("vibrate"), true));
        if (body.containsKey("advanceMinutes")) {
            cur.put("advanceMinutes", ViewUtil.clamp(ViewUtil.toInt(body.get("advanceMinutes"), 0), 0, 120));
        }
        if (body.containsKey("quietHoursStart")) {
            String v = ViewUtil.toStr(body.get("quietHoursStart"));
            if (v != null && !isValidTime(v)) throw BizException.badRequest("免打扰开始时间格式应为 HH:mm");
            cur.put("quietHoursStart", v);
        }
        if (body.containsKey("quietHoursEnd")) {
            String v = ViewUtil.toStr(body.get("quietHoursEnd"));
            if (v != null && !isValidTime(v)) throw BizException.badRequest("免打扰结束时间格式应为 HH:mm");
            cur.put("quietHoursEnd", v);
        }
        if (body.containsKey("checkinReminderEnabled")) {
            cur.put("checkinReminderEnabled", ViewUtil.toBool(body.get("checkinReminderEnabled"), true));
        }
        if (body.containsKey("language")) cur.put("language", ViewUtil.toStr(body.get("language"), "zh-CN"));

        cur.put("updatedAt", LocalDateTime.now().toString());
        writeSettings(targetId, cur);

        cur.put("userId", targetId);
        return ApiResponse.ok("设置已保存", cur);
    }

    /**
     * 打卡提醒配置
     *
     * GET /api/reminder/checkin-reminder
     *
     * 从 medicine_reminders 里取 __checkin__ 前缀的记录，
     * 没有则返回默认值（22:00 提醒睡觉 / 06:30 提醒起床）。
     */
    @GetMapping("/checkin-reminder")
    public ApiResponse<Map<String, Object>> getCheckinReminder(HttpServletRequest req,
                                                               @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));
        String targetId = blankToNull(userId) == null ? me.getId() : userId;

        List<MedicineReminder> rows = reminderMapper.selectByUser(targetId, null);

        Map<String, Object> data = new LinkedHashMap<>();
        MedicineReminder found = null;
        for (MedicineReminder r : rows) {
            if (r.getMedicineName() != null && r.getMedicineName().startsWith(PREFIX_CHECKIN)) {
                found = r;
                break;
            }
        }

        if (found == null) {
            data.put("configured", false);
            data.put("enabled", true);
            data.put("sleepTime", "22:00");
            data.put("wakeTime", "06:30");
            data.put("reminderId", null);
        } else {
            List<String> times = parseJsonArray(found.getTimes());
            data.put("configured", true);
            data.put("enabled", found.getIsActive());
            data.put("sleepTime", times.size() > 0 ? times.get(0) : "22:00");
            data.put("wakeTime", times.size() > 1 ? times.get(1) : "06:30");
            data.put("reminderId", found.getId());
            data.put("notes", found.getNotes());
        }

        Map<String, Object> settings = readSettings(targetId);
        data.put("settingsEnabled", settings.get("checkinReminderEnabled"));
        data.put("userId", targetId);
        return ApiResponse.ok(data);
    }

    /**
     * 设置打卡提醒
     *
     * 请求体：{ "sleepTime": "22:00", "wakeTime": "06:30", "enabled": true }
     */
    @PostMapping("/checkin-reminder")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Map<String, Object>> setCheckinReminder(HttpServletRequest req,
                                                               @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) body = new LinkedHashMap<>();

        String targetId = ViewUtil.toStr(body.get("userId"), me.getId());

        String sleepTime = ViewUtil.toStr(body.get("sleepTime"), "22:00");
        String wakeTime = ViewUtil.toStr(body.get("wakeTime"), "06:30");
        if (!isValidTime(sleepTime)) throw BizException.badRequest("睡觉提醒时间格式应为 HH:mm");
        if (!isValidTime(wakeTime)) throw BizException.badRequest("起床提醒时间格式应为 HH:mm");

        boolean enabled = ViewUtil.toBool(body.get("enabled"), true);

        // 查找已有的打卡提醒
        List<MedicineReminder> rows = reminderMapper.selectByUser(targetId, null);
        MedicineReminder exist = null;
        for (MedicineReminder r : rows) {
            if (r.getMedicineName() != null && r.getMedicineName().startsWith(PREFIX_CHECKIN)) {
                exist = r;
                break;
            }
        }

        String id;
        if (exist == null) {
            MedicineReminder r = new MedicineReminder();
            r.setId(CommonUtil.uuid());
            r.setUserId(targetId);
            r.setMedicineName(PREFIX_CHECKIN + "作息打卡提醒");
            r.setDosage("-");
            r.setFrequency("twice");
            r.setTimes(toJsonArray(List.of(sleepTime, wakeTime)));
            r.setStartDate(LocalDate.now());
            r.setNotes("自动生成的作息打卡提醒");
            r.setIsActive(enabled);
            reminderMapper.insert(r);
            id = r.getId();
        } else {
            id = exist.getId();
            MedicineReminder patch = new MedicineReminder();
            patch.setId(id);
            patch.setTimes(toJsonArray(List.of(sleepTime, wakeTime)));
            patch.setIsActive(enabled);
            reminderMapper.update(patch);
        }

        log.info("用户 {} 打卡提醒已设置：睡 {} / 起 {}，启用={}", targetId, sleepTime, wakeTime, enabled);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reminderId", id);
        data.put("sleepTime", sleepTime);
        data.put("wakeTime", wakeTime);
        data.put("enabled", enabled);
        data.put("configured", true);
        return ApiResponse.ok("打卡提醒已保存", data);
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

    /** 提醒视图 */
    private Map<String, Object> toReminderView(MedicineReminder r) {
        if (r == null) return new LinkedHashMap<>();
        String type = typeOf(r.getMedicineName());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("userId", r.getUserId());
        m.put("type", type);
        m.put("typeText", typeLabel(type));
        m.put("title", displayName(r.getMedicineName()));
        m.put("medicineName", "medicine".equals(type) ? r.getMedicineName() : null);
        m.put("dosage", r.getDosage());
        m.put("frequency", r.getFrequency());
        m.put("frequencyText", frequencyLabel(r.getFrequency()));
        m.put("times", parseJsonArray(r.getTimes()));
        m.put("startDate", ViewUtil.dateStr(r.getStartDate()));
        m.put("endDate", ViewUtil.dateStr(r.getEndDate()));
        m.put("notes", r.getNotes());
        m.put("enabled", r.getIsActive());
        m.put("createTime", ViewUtil.dateTimeStr(r.getCreatedAt()));
        return m;
    }

    /** 判断提醒归属，防止越权修改他人提醒 */
    private void assertOwner(MedicineReminder r, User me) {
        if (r.getUserId() != null && !r.getUserId().equals(me.getId())) {
            throw BizException.forbidden("无权操作他人的提醒");
        }
    }

    /** 从 medicine_name 推断业务类型 */
    private String typeOf(String medicineName) {
        if (medicineName == null) return "medicine";
        if (medicineName.startsWith(PREFIX_CHECKIN)) return "checkin";
        if (medicineName.startsWith(PREFIX_CUSTOM)) return "custom";
        return "medicine";
    }

    /** 取原有的类型前缀（用于更新时保留类型） */
    private String typePrefix(String medicineName) {
        String t = typeOf(medicineName);
        return switch (t) {
            case "checkin" -> PREFIX_CHECKIN;
            case "custom" -> PREFIX_CUSTOM;
            default -> "";
        };
    }

    /** 去掉内部前缀后的展示名 */
    private String displayName(String medicineName) {
        if (medicineName == null) return "提醒";
        if (medicineName.startsWith(PREFIX_CHECKIN)) {
            return medicineName.substring(PREFIX_CHECKIN.length());
        }
        if (medicineName.startsWith(PREFIX_CUSTOM)) {
            return medicineName.substring(PREFIX_CUSTOM.length());
        }
        return medicineName;
    }

    private String normalizeType(String type) {
        if (type == null) return "medicine";
        return switch (type) {
            case "checkin", "medicine", "custom" -> type;
            default -> "medicine";
        };
    }

    private String typeLabel(String type) {
        if (type == null) return "提醒";
        return switch (type) {
            case "checkin" -> "打卡提醒";
            case "medicine" -> "用药提醒";
            case "custom" -> "自定义提醒";
            default -> "提醒";
        };
    }

    private String frequencyLabel(String f) {
        if (f == null) return "每日";
        return switch (f) {
            case "daily" -> "每日一次";
            case "twice" -> "每日两次";
            case "three_times" -> "每日三次";
            case "weekly" -> "每周";
            case "monthly" -> "每月";
            default -> f;
        };
    }

    private String eventLabel(String event) {
        if (event == null) return "提醒事件";
        return switch (event) {
            case "taken" -> "已服药";
            case "missed" -> "漏服";
            case "skipped" -> "跳过";
            case "snooze" -> "稍后提醒";
            case "trigger" -> "提醒已触发";
            case "confirm" -> "已确认";
            default -> "提醒事件：" + event;
        };
    }

    private String defaultDosage(String type) {
        return "medicine".equals(type) ? "1次" : "-";
    }

    /** 按时间点数量推断频次 */
    private String defaultFrequency(int timeCount) {
        return switch (timeCount) {
            case 1 -> "daily";
            case 2 -> "twice";
            case 3 -> "three_times";
            default -> timeCount > 3 ? "three_times" : "daily";
        };
    }

    /** HH:mm 校验 */
    private boolean isValidTime(String t) {
        if (t == null) return false;
        String[] parts = t.split(":");
        if (parts.length != 2) return false;
        try {
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            return h >= 0 && h <= 23 && m >= 0 && m <= 59;
        } catch (Exception e) {
            return false;
        }
    }

    /** 比较 "HH:mm" 是否早于 now */
    private boolean isBefore(String hhmm, LocalTime now) {
        if (!isValidTime(hhmm)) return false;
        String[] p = hhmm.split(":");
        return (Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]))
                < (now.getHour() * 60 + now.getMinute());
    }

    // ---------------- 提醒日志（Redis 列表，最多保留 200 条） ----------------

    private void appendLog(String userId, Map<String, Object> entry) {
        try {
            String key = LOG_KEY + userId;
            redis.opsForList().leftPush(key, toJson(entry));
            redis.opsForList().trim(key, 0, 199);
            redis.expire(key, 90, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("提醒日志写入失败（Redis 不可用）: {}", e.getMessage());
        }
    }

    private List<Map<String, Object>> readLogs(String userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            List<String> raw = redis.opsForList().range(LOG_KEY + userId, 0, 499);
            if (raw == null) return out;
            for (String s : raw) {
                out.add(parseFlatJson(s));
            }
        } catch (Exception e) {
            log.warn("提醒日志读取失败（Redis 不可用）: {}", e.getMessage());
        }
        return out;
    }

    // ---------------- 提醒设置（Redis Hash） ----------------

    private Map<String, Object> readSettings(String userId) {
        Map<String, Object> def = defaultSettings();
        try {
            Map<Object, Object> raw = redis.opsForHash().entries(SETTINGS_KEY + userId);
            if (raw == null || raw.isEmpty()) return def;
            for (Map.Entry<Object, Object> e : raw.entrySet()) {
                String k = String.valueOf(e.getKey());
                Object v = e.getValue();
                // 按默认值的类型还原
                Object dv = def.get(k);
                if (dv instanceof Boolean) def.put(k, ViewUtil.toBool(v, true));
                else if (dv instanceof Integer) def.put(k, ViewUtil.toInt(v, 0));
                else def.put(k, ViewUtil.str(v));
            }
        } catch (Exception e) {
            log.warn("提醒设置读取失败（Redis 不可用，返回默认值）: {}", e.getMessage());
        }
        return def;
    }

    private void writeSettings(String userId, Map<String, Object> settings) {
        try {
            Map<String, String> flat = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : settings.entrySet()) {
                if (e.getValue() != null) flat.put(e.getKey(), String.valueOf(e.getValue()));
            }
            redis.opsForHash().putAll(SETTINGS_KEY + userId, flat);
        } catch (Exception e) {
            log.warn("提醒设置写入失败（Redis 不可用）: {}", e.getMessage());
        }
    }

    private Map<String, Object> defaultSettings() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", true);
        m.put("sound", true);
        m.put("vibrate", true);
        m.put("advanceMinutes", 5);
        m.put("quietHoursStart", "22:30");
        m.put("quietHoursEnd", "06:30");
        m.put("checkinReminderEnabled", true);
        m.put("language", "zh-CN");
        m.put("updatedAt", null);
        return m;
    }

    // ---------------- JSON 处理（轻量实现，避免引入额外依赖） ----------------

    /** List<String> 转 JSON 数组字符串 */
    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(list.get(i).replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }

    /** 解析 JSON 数组字符串为字符串列表 */
    private List<String> parseJsonArray(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isBlank()) return out;
        String s = json.trim();
        if (s.startsWith("[")) s = s.substring(1);
        if (s.endsWith("]")) s = s.substring(0, s.length() - 1);

        for (String part : s.split(",")) {
            String v = part.trim();
            if (v.startsWith("\"")) v = v.substring(1);
            if (v.endsWith("\"")) v = v.substring(0, v.length() - 1);
            v = v.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    /** 平铺 Map 转 JSON 字符串 */
    private String toJson(Map<String, Object> m) {
        if (m == null) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"')
                  .append(String.valueOf(v).replace("\\", "\\\\").replace("\"", "\\\""))
                  .append('"');
            }
        }
        return sb.append('}').toString();
    }

    /** 解析平铺 JSON 对象为 Map */
    private Map<String, Object> parseFlatJson(String json) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return m;

        String s = json.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);

        int i = 0;
        while (i < s.length()) {
            int ks = s.indexOf('"', i);
            if (ks < 0) break;
            int ke = s.indexOf('"', ks + 1);
            if (ke < 0) break;
            String key = s.substring(ks + 1, ke);

            int colon = s.indexOf(':', ke);
            if (colon < 0) break;

            int vs = colon + 1;
            while (vs < s.length() && s.charAt(vs) == ' ') vs++;

            Object value;
            int next;
            if (vs < s.length() && s.charAt(vs) == '"') {
                int ve = vs + 1;
                StringBuilder sb = new StringBuilder();
                while (ve < s.length()) {
                    char c = s.charAt(ve);
                    if (c == '\\' && ve + 1 < s.length()) {
                        sb.append(s.charAt(ve + 1));
                        ve += 2;
                        continue;
                    }
                    if (c == '"') break;
                    sb.append(c);
                    ve++;
                }
                value = sb.toString();
                next = ve + 1;
            } else {
                int ve = s.indexOf(',', vs);
                if (ve < 0) ve = s.length();
                String raw = s.substring(vs, ve).trim();
                value = parseScalar(raw);
                next = ve;
            }

            m.put(key, value);

            int comma = s.indexOf(',', next);
            if (comma < 0) break;
            i = comma + 1;
        }
        return m;
    }

    private Object parseScalar(String raw) {
        if (raw == null || raw.isEmpty() || "null".equals(raw)) return null;
        if ("true".equalsIgnoreCase(raw)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(raw)) return Boolean.FALSE;
        try {
            return Double.parseDouble(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** 保留给告警联动的入口（提醒长时间未确认时可升级） */
    @SuppressWarnings("unused")
    private void escalate(String userId, String title, String description) {
        com.guardian.entity.Alert alert = new com.guardian.entity.Alert();
        alert.setId(CommonUtil.uuid());
        alert.setUserId(userId);
        alert.setAlertType("reminder_missed");
        alert.setSeverity("low");
        alert.setTitle(title);
        alert.setDescription(description);
        alert.setOccurredAt(LocalDateTime.now());
        alert.setHandled(false);
        alertMapper.insert(alert);
    }
}
