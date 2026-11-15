package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.entity.*;
import com.guardian.mapper.*;
import com.guardian.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 居家守护接口
 *
 * 路由：
 *   POST /api/guardian/sos          SOS 求救
 *   GET  /api/guardian/status       长辈状态
 *   GET  /api/guardian/device       设备状态
 *   GET  /api/guardian/alerts       告警列表
 *   POST /api/guardian/alert/handle 处理告警
 */
@Slf4j
@RestController
@RequestMapping("/guardian")
@RequiredArgsConstructor
public class GuardianController {

    private final AlertMapper alertMapper;
    private final DeviceMapper deviceMapper;
    private final CheckinRecordMapper checkinMapper;
    private final FamilyBindingMapper bindingMapper;
    private final UserMapper userMapper;
    private final AuthService authService;

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    /**
     * SOS 求救
     *
     * 请求体：{ "userId": "...", "reason": "跌倒", "location": "客厅", "lng": 116.4, "lat": 39.9 }
     *
     * ★ 会产生 critical 级别告警，家属端能看到
     */
    @PostMapping("/sos")
    public ApiResponse<Map<String, Object>> sos(HttpServletRequest req,
                                                @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));

        String reason = body == null ? null : String.valueOf(body.getOrDefault("reason", "紧急求助"));
        String location = body == null ? null : String.valueOf(body.getOrDefault("location", "未知位置"));

        Alert alert = new Alert();
        alert.setId(CommonUtil.uuid());
        alert.setUserId(me.getId());
        alert.setAlertType("sos");
        alert.setSeverity("critical");
        alert.setTitle("紧急求助");
        alert.setDescription(String.format("用户 %s 在【%s】发起 SOS：%s",
                me.getName() == null ? CommonUtil.maskPhone(me.getPhone()) : me.getName(),
                location, reason));
        alert.setOccurredAt(LocalDateTime.now());
        alert.setHandled(false);

        alertMapper.insert(alert);
        log.warn("【SOS】用户 {} 发起求助，位置={}，原因={}", me.getId(), location, reason);

        // 查出紧急联系人（已绑定的家属）
        List<FamilyBinding> bindings = bindingMapper.selectByElder(me.getId());
        List<Map<String, Object>> contacts = new ArrayList<>();
        for (FamilyBinding b : bindings) {
            if (!"accepted".equals(b.getStatus())) continue;
            User fam = userMapper.selectById(b.getFamilyId());
            if (fam == null) continue;
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("name", fam.getName());
            c.put("phone", CommonUtil.maskPhone(fam.getPhone()));
            contacts.add(c);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alertId", alert.getId());
        data.put("notified", contacts.size());
        data.put("contacts", contacts);
        data.put("occurredAt", alert.getOccurredAt().toString());
        data.put("message", contacts.isEmpty()
                ? "已记录求助。你还没有绑定家属，建议尽快在「家庭成员」中添加紧急联系人。"
                : "已通知 " + contacts.size() + " 位家属");

        return ApiResponse.ok(data);
    }

    /**
     * 长辈当前状态
     *
     * 原项目的 deviceStatus / elderStatus 是死值，这里从真实数据推导。
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status(HttpServletRequest req,
                                                   @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));
        String targetId = (userId == null || userId.isBlank()) ? me.getId() : userId;

        User target = userMapper.selectById(targetId);
        if (target == null) {
            throw BizException.notFound("用户不存在");
        }

        LocalDate today = LocalDate.now();
        CheckinRecord checkin = checkinMapper.selectByUserAndDate(targetId, today);

        // 未处理告警数
        List<Alert> pending = alertMapper.selectByUserAndRange(
                targetId, LocalDateTime.now().minusDays(7), LocalDateTime.now());
        long unhandled = pending.stream().filter(a -> Boolean.FALSE.equals(a.getHandled())).count();

        // 从打卡状态推导老人状态
        String elderStatus;
        if (checkin == null) {
            elderStatus = "unknown";                        // 今日未打卡
        } else if (checkin.getSleepTime() != null && checkin.getWakeTime() == null) {
            elderStatus = "sleeping";                       // 已睡未起
        } else if (checkin.getWakeTime() != null) {
            elderStatus = "awake";                          // 已起床
        } else {
            elderStatus = "unknown";
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", targetId);
        data.put("name", target.getName());
        data.put("avatar", target.getAvatar());

        Map<String, Object> st = new LinkedHashMap<>();
        st.put("code", elderStatus);
        st.put("text", switch (elderStatus) {
            case "sleeping" -> "休息中";
            case "awake" -> "活动中";
            default -> "今日未打卡";
        });
        data.put("elderStatus", st);

        Map<String, Object> ck = new LinkedHashMap<>();
        ck.put("sleepTime", checkin == null ? null : checkin.getSleepTime());
        ck.put("wakeTime", checkin == null ? null : checkin.getWakeTime());
        ck.put("sleepScore", checkin == null ? null : checkin.getSleepScore());
        data.put("checkin", ck);

        data.put("unhandledAlerts", unhandled);
        data.put("safe", unhandled == 0);

        return ApiResponse.ok(data);
    }

    /**
     * 设备状态
     *
     * 原项目这个接口的成功分支是空注释块，不更新任何 state，这里返回真实设备列表。
     */
    @GetMapping("/device")
    public ApiResponse<Map<String, Object>> device(HttpServletRequest req,
                                                   @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));
        String targetId = (userId == null || userId.isBlank()) ? me.getId() : userId;

        List<Device> devices = deviceMapper.selectByUser(targetId);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Device d : devices) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("name", d.getDeviceName());
            m.put("type", d.getDeviceType());
            m.put("status", d.getStatus());
            m.put("statusText", switch (d.getStatus() == null ? "" : d.getStatus()) {
                case "active" -> "在线";
                case "offline" -> "离线";
                case "unbound" -> "已解绑";
                default -> "未知";
            });
            m.put("lastSync", d.getLastSync() == null ? null : d.getLastSync().toString());
            list.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        data.put("onlineCount", list.stream()
                .filter(m -> "active".equals(m.get("status"))).count());
        return ApiResponse.ok(data);
    }

    /** 告警列表 */
    @GetMapping("/alerts")
    public ApiResponse<Map<String, Object>> alerts(HttpServletRequest req,
                                                   @RequestParam(required = false) Boolean handled,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int pageSize) {
        User me = authService.requireUser(token(req));

        int p = Math.max(1, page);
        int size = Math.max(1, Math.min(pageSize, 100));

        // 家属视角：能看到绑定长辈的告警
        List<String> targetIds = new ArrayList<>();
        targetIds.add(me.getId());
        for (FamilyBinding b : bindingMapper.selectByFamily(me.getId())) {
            if ("accepted".equals(b.getStatus())) {
                targetIds.add(b.getElderId());
            }
        }

        List<Map<String, Object>> all = new ArrayList<>();
        for (String uid : targetIds) {
            List<Alert> rows = alertMapper.selectPage(uid, handled, 0, 200);
            for (Alert a : rows) {
                all.add(toAlertView(a));
            }
        }

        // 按时间倒序
        all.sort((x, y) -> String.valueOf(y.get("occurredAt")).compareTo(String.valueOf(x.get("occurredAt"))));

        int from = Math.min((p - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        List<Map<String, Object>> pageList = all.subList(from, to);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", pageList);
        data.put("total", all.size());
        data.put("page", p);
        data.put("pageSize", size);
        data.put("hasMore", to < all.size());
        return ApiResponse.ok(data);
    }

    /** 处理告警 */
    @PostMapping("/alert/handle")
    public ApiResponse<Map<String, Object>> handleAlert(HttpServletRequest req,
                                                        @RequestBody Map<String, String> body) {
        User me = authService.requireUser(token(req));

        String alertId = body == null ? null : body.get("id");
        if (alertId == null || alertId.isBlank()) {
            throw BizException.badRequest("缺少告警 ID");
        }

        Alert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw BizException.notFound("告警不存在");
        }
        if (Boolean.TRUE.equals(alert.getHandled())) {
            throw BizException.badRequest("该告警已处理过了");
        }

        String notes = body.get("notes");
        alertMapper.updateHandled(alertId, me.getId(), notes);

        log.info("告警 {} 已由 {} 处理", alertId, me.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("id", alertId);
        data.put("message", "已标记为处理完成");
        return ApiResponse.ok(data);
    }

    /** 手动创建告警（设备上报用） */
    @PostMapping("/alert/create")
    public ApiResponse<Map<String, Object>> createAlert(HttpServletRequest req,
                                                        @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));

        String type = body == null ? "abnormal" : String.valueOf(body.getOrDefault("alertType", "abnormal"));
        String severity = body == null ? "medium" : String.valueOf(body.getOrDefault("severity", "medium"));
        String title = body == null ? "健康异常" : String.valueOf(body.getOrDefault("title", "健康异常"));

        Alert alert = new Alert();
        alert.setId(CommonUtil.uuid());
        alert.setUserId(me.getId());
        alert.setAlertType(type);
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setDescription(body == null ? null : (String) body.get("description"));
        alert.setOccurredAt(LocalDateTime.now());
        alert.setHandled(false);

        alertMapper.insert(alert);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", alert.getId());
        data.put("created", true);
        return ApiResponse.ok(data);
    }

    /** 告警统计 */
    @GetMapping("/alert/stats")
    public ApiResponse<Map<String, Object>> alertStats(HttpServletRequest req) {
        User me = authService.requireUser(token(req));

        List<Alert> week = alertMapper.selectByUserAndRange(
                me.getId(), LocalDateTime.now().minusDays(7), LocalDateTime.now());
        List<Alert> month = alertMapper.selectByUserAndRange(
                me.getId(), LocalDateTime.now().minusDays(30), LocalDateTime.now());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("weekTotal", week.size());
        data.put("weekUnhandled", week.stream().filter(a -> Boolean.FALSE.equals(a.getHandled())).count());
        data.put("monthTotal", month.size());

        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (String s : List.of("critical", "high", "medium", "low")) {
            bySeverity.put(s, month.stream()
                    .filter(a -> s.equals(a.getSeverity())).count());
        }
        data.put("bySeverity", bySeverity);
        return ApiResponse.ok(data);
    }

    // ---------------- 视图转换 ----------------

    private Map<String, Object> toAlertView(Alert a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("type", a.getAlertType());
        m.put("typeText", switch (a.getAlertType() == null ? "" : a.getAlertType()) {
            case "sos" -> "紧急求助";
            case "fall" -> "跌倒警报";
            case "abnormal" -> "健康异常";
            default -> "长时间未移动";
        });
        m.put("severity", a.getSeverity());
        m.put("severityText", switch (a.getSeverity() == null ? "" : a.getSeverity()) {
            case "critical" -> "紧急";
            case "high" -> "严重";
            case "medium" -> "注意";
            default -> "提示";
        });
        m.put("title", a.getTitle());
        m.put("description", a.getDescription());
        m.put("occurredAt", a.getOccurredAt() == null ? null : a.getOccurredAt().toString());
        m.put("handled", a.getHandled());
        m.put("handledAt", a.getHandledAt() == null ? null : a.getHandledAt().toString());
        m.put("notes", a.getNotes());
        return m;
    }
}
