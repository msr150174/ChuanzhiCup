package com.guardian.service;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.entity.MedicineRecord;
import com.guardian.entity.MedicineReminder;
import com.guardian.mapper.MedicineRecordMapper;
import com.guardian.mapper.MedicineReminderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 用药服务
 *
 * 负责用药提醒的增删改查、服药记录、今日服药情况与依从率统计。
 *
 * 时段划分规则（固定）：
 *   hour < 12  → morning   （晨起）
 *   hour < 18  → afternoon （午后）
 *   else       → evening   （睡前）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicineService {

    private final MedicineReminderMapper reminderMapper;
    private final MedicineRecordMapper recordMapper;

    /** 三个固定时段 */
    private static final List<String> PERIODS = List.of("morning", "afternoon", "evening");

    private static final Map<String, String> PERIOD_TEXTS = Map.of(
            "morning", "晨起",
            "afternoon", "午后",
            "evening", "睡前");

    private static final Map<String, String> STATUS_TEXTS = Map.of(
            "taken", "已服用",
            "missed", "漏服",
            "skipped", "已跳过");

    private Integer toInt(Object o) {
        if (o == null) return null;
        try {
            return (int) Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按小时划分时段
     *
     * hour < 12 → morning，hour < 18 → afternoon，否则 evening
     */
    private static String periodOf(int hour) {
        if (hour < 12) return "morning";
        if (hour < 18) return "afternoon";
        return "evening";
    }

    /** 从 "HH:mm" 解析出时段，非法时间默认归入 morning */
    private static String periodOfTime(String hhmm) {
        if (hhmm == null || hhmm.isBlank()) return "morning";
        try {
            String[] parts = hhmm.split(":");
            return periodOf(Integer.parseInt(parts[0].trim()));
        } catch (Exception e) {
            return "morning";
        }
    }

    // ========================================================================
    // 提醒：查询
    // ========================================================================

    /**
     * 用药提醒列表
     *
     * 顺带算出每个提醒今天的完成情况（该提醒的时段是否已打卡）。
     */
    public Map<String, Object> getReminders(String userId, Boolean isActive) {
        List<MedicineReminder> reminders = reminderMapper.selectByUser(userId, isActive);

        LocalDate today = LocalDate.now();
        List<MedicineRecord> todayRecords = recordMapper.selectByUserAndDate(userId, today);

        List<Map<String, Object>> list = new ArrayList<>();
        for (MedicineReminder r : reminders) {
            list.add(toReminderView(r, todayRecords));
        }

        long active = list.stream().filter(m -> Boolean.TRUE.equals(m.get("isActive"))).count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        data.put("activeCount", active);
        return data;
    }

    // ========================================================================
    // 提醒：写入
    // ========================================================================

    /**
     * 新增用药提醒
     *
     * 请求体：{ "medicineName": "降压药", "dosage": "1片", "frequency": "twice",
     *          "times": ["08:00","20:00"], "startDate": "2026-02-14",
     *          "endDate": null, "notes": "" }
     * 兼容 medicineName / name 与 times 的数组或逗号分隔字符串。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addReminder(String userId, Map<String, Object> body) {
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }

        String name = str(body.getOrDefault("medicineName", body.get("name")));
        if (name == null) {
            throw BizException.badRequest("请填写药品名称");
        }

        String dosage = str(body.get("dosage"));
        if (dosage == null) {
            throw BizException.badRequest("请填写剂量");
        }

        String timesJson = normalizeTimes(body.get("times"));
        if (timesJson == null) {
            throw BizException.badRequest("请至少设置一个提醒时间点");
        }

        String frequency = str(body.get("frequency"));
        if (frequency == null) {
            // 未指定频次时按时间点数量推断
            frequency = switch (countTimes(timesJson)) {
                case 1 -> "daily";
                case 2 -> "twice";
                case 3 -> "three_times";
                default -> "daily";
            };
        }

        MedicineReminder r = new MedicineReminder();
        r.setId(CommonUtil.uuid());
        r.setUserId(userId);
        r.setMedicineName(name);
        r.setDosage(dosage);
        r.setFrequency(frequency);
        r.setTimes(timesJson);
        r.setStartDate(parseDate(body.get("startDate"), LocalDate.now()));
        r.setEndDate(parseDateOrNull(body.get("endDate")));
        r.setNotes(str(body.get("notes")));
        r.setIsActive(body.get("isActive") == null || Boolean.parseBoolean(String.valueOf(body.get("isActive"))));

        reminderMapper.insert(r);
        log.info("用户 {} 新增用药提醒 {}（{}）", userId, r.getId(), name);

        return toReminderView(r, List.of());
    }

    /**
     * 更新用药提醒
     *
     * 请求体：{ "id": "...", "medicineName": "...", "dosage": "...", "isActive": true }
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateReminder(String userId, Map<String, Object> body) {
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }

        String id = str(body.get("id"));
        if (id == null) {
            throw BizException.badRequest("缺少提醒 ID");
        }

        MedicineReminder existing = reminderMapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("提醒不存在");
        }
        if (!Objects.equals(existing.getUserId(), userId)) {
            throw BizException.forbidden("无权修改该提醒");
        }

        MedicineReminder r = new MedicineReminder();
        r.setId(id);
        r.setMedicineName(str(body.getOrDefault("medicineName", body.get("name"))));
        r.setDosage(str(body.get("dosage")));
        r.setFrequency(str(body.get("frequency")));

        String timesJson = normalizeTimes(body.get("times"));
        r.setTimes(timesJson);

        r.setStartDate(parseDateOrNull(body.get("startDate")));
        r.setEndDate(parseDateOrNull(body.get("endDate")));

        // notes 允许显式清空
        if (body.containsKey("notes")) {
            Object notes = body.get("notes");
            r.setNotes(notes == null ? "" : String.valueOf(notes));
        }

        if (body.containsKey("isActive")) {
            Object active = body.get("isActive");
            r.setIsActive(active == null || Boolean.parseBoolean(String.valueOf(active)));
        }

        reminderMapper.update(r);
        log.info("用户 {} 更新用药提醒 {}", userId, id);

        MedicineReminder updated = reminderMapper.selectById(id);
        return toReminderView(updated == null ? existing : updated, List.of());
    }

    /** 删除用药提醒（软删除，置 is_active=0 保留历史） */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deleteReminder(String userId, String id) {
        if (id == null || id.isBlank()) {
            throw BizException.badRequest("缺少提醒 ID");
        }

        MedicineReminder existing = reminderMapper.selectById(id);
        if (existing == null) {
            throw BizException.notFound("提醒不存在");
        }
        if (!Objects.equals(existing.getUserId(), userId)) {
            throw BizException.forbidden("无权删除该提醒");
        }

        reminderMapper.deleteById(id);
        log.info("用户 {} 删除用药提醒 {}", userId, id);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("id", id);
        data.put("message", "提醒已删除");
        return data;
    }

    // ========================================================================
    // 服药记录
    // ========================================================================

    /**
     * 记录服药
     *
     * 请求体：{ "reminderId": "...", "status": "taken|missed|skipped",
     *          "taken": true, "time": "08:05", "notes": "" }
     *
     * 兼容旧的 taken 布尔写法：taken=true → taken，taken=false → missed。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recordMedicine(String userId, Map<String, Object> body) {
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }

        String reminderId = str(body.get("reminderId"));
        MedicineReminder reminder = null;
        if (reminderId != null) {
            reminder = reminderMapper.selectById(reminderId);
            if (reminder == null) {
                throw BizException.notFound("提醒不存在");
            }
            if (!Objects.equals(reminder.getUserId(), userId)) {
                throw BizException.forbidden("无权操作该提醒");
            }
        }

        // 确定药品名称
        String medicineName = str(body.getOrDefault("medicineName", body.get("name")));
        if (medicineName == null && reminder != null) {
            medicineName = reminder.getMedicineName();
        }
        if (medicineName == null) {
            throw BizException.badRequest("请提供药品名称或关联的提醒 ID");
        }

        // 确定状态：status 优先，其次看 taken 布尔
        String status = str(body.get("status"));
        if (status == null) {
            Object taken = body.get("taken");
            if (taken != null) {
                status = Boolean.parseBoolean(String.valueOf(taken)) ? "taken" : "missed";
            }
        }
        if (status == null) {
            status = "taken";
        }
        if (!STATUS_TEXTS.containsKey(status)) {
            throw BizException.badRequest("状态必须是 taken/missed/skipped 之一");
        }

        LocalDate date = parseDate(body.get("date"), LocalDate.now());
        LocalTime now = LocalTime.now();

        // 服药时间：显式传入 HH:mm 时按当天拼装
        LocalDateTime takenAt = LocalDateTime.of(date, now);
        String timeStr = str(body.getOrDefault("time", body.get("takenTime")));
        if (timeStr != null) {
            try {
                takenAt = LocalDateTime.of(date, LocalTime.parse(timeStr));
            } catch (Exception e) {
                log.warn("服药时间 {} 解析失败，使用当前时间", timeStr);
            }
        }

        // 同一天同一药品已有记录则更新，避免重复打卡产生多行
        MedicineRecord existing = recordMapper.selectByUserDateAndMedicine(
                userId, date, medicineName, reminderId);
        if (existing != null) {
            recordMapper.updateStatus(existing.getId(), status, takenAt, str(body.get("notes")));
            existing.setStatus(status);
            existing.setTakenTime(takenAt);
            existing.setNotes(str(body.get("notes")));
            log.info("用户 {} 更新服药记录 {} → {}", userId, existing.getId(), status);
            return toRecordView(existing);
        }

        MedicineRecord r = new MedicineRecord();
        r.setId(CommonUtil.uuid());
        r.setReminderId(reminderId);
        r.setUserId(userId);
        r.setMedicineName(medicineName);
        r.setTakenTime(takenAt);
        r.setTakenDate(date);
        r.setStatus(status);
        r.setNotes(str(body.get("notes")));

        recordMapper.insert(r);
        log.info("用户 {} 记录服药 {}（{}，{}）", userId, r.getId(), medicineName, status);

        return toRecordView(r);
    }

    /**
     * 今日服药情况
     *
     * 按 morning / afternoon / evening 三个时段组织：
     * 每个时段列出该时段应服的药（来自启用的提醒）以及是否已服。
     */
    public Map<String, Object> today(String userId) {
        LocalDate today = LocalDate.now();
        List<MedicineReminder> reminders = reminderMapper.selectByUser(userId, true);
        List<MedicineRecord> records = recordMapper.selectByUserAndDate(userId, today);

        // 提醒 ID -> 该提醒当天的记录
        Map<String, MedicineRecord> recordByReminder = new HashMap<>();
        for (MedicineRecord rec : records) {
            if (rec.getReminderId() != null) {
                // 同一提醒同一天可能多条（多时段），保留最新一条
                MedicineRecord old = recordByReminder.get(rec.getReminderId());
                if (old == null || (rec.getTakenTime() != null && old.getTakenTime() != null
                        && rec.getTakenTime().isAfter(old.getTakenTime()))) {
                    recordByReminder.put(rec.getReminderId(), rec);
                }
            }
        }

        // 逐时段归集
        Map<String, List<Map<String, Object>>> periods = new LinkedHashMap<>();
        for (String p : PERIODS) {
            periods.put(p, new ArrayList<>());
        }

        int totalScheduled = 0;
        int totalTaken = 0;

        for (MedicineReminder r : reminders) {
            // 提醒的有效期判断
            if (r.getStartDate() != null && today.isBefore(r.getStartDate())) continue;
            if (r.getEndDate() != null && today.isAfter(r.getEndDate())) continue;

            List<String> times = parseTimes(r.getTimes());
            if (times.isEmpty()) times = List.of("08:00");

            MedicineRecord rec = recordByReminder.get(r.getId());

            for (String t : times) {
                String period = periodOfTime(t);
                totalScheduled++;

                boolean taken = rec != null && "taken".equals(rec.getStatus());
                if (taken) totalTaken++;

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("reminderId", r.getId());
                item.put("medicineName", r.getMedicineName());
                item.put("dosage", r.getDosage());
                item.put("frequency", r.getFrequency());
                item.put("scheduledTime", t);
                item.put("period", period);
                item.put("periodText", PERIOD_TEXTS.get(period));
                item.put("recordId", rec == null ? null : rec.getId());
                item.put("status", rec == null ? "pending" : rec.getStatus());
                item.put("statusText", rec == null ? "待服药"
                        : STATUS_TEXTS.getOrDefault(rec.getStatus(), "待服药"));
                item.put("taken", taken);
                item.put("takenTime", rec == null || rec.getTakenTime() == null
                        ? null : rec.getTakenTime().toString());
                item.put("notes", r.getNotes());

                periods.get(period).add(item);
            }
        }

        // 时段内按计划时间排序
        for (List<Map<String, Object>> items : periods.values()) {
            items.sort(Comparator.comparing(m -> String.valueOf(m.get("scheduledTime"))));
        }

        // 未关联提醒的自服记录
        List<Map<String, Object>> extra = new ArrayList<>();
        for (MedicineRecord rec : records) {
            if (rec.getReminderId() == null) {
                extra.add(toRecordView(rec));
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", today.toString());
        data.put("periods", periods);
        data.put("periodTexts", new LinkedHashMap<>(PERIOD_TEXTS));
        data.put("totalScheduled", totalScheduled);
        data.put("totalTaken", totalTaken);
        data.put("pendingCount", totalScheduled - totalTaken);
        data.put("completed", totalScheduled > 0 && totalTaken >= totalScheduled);
        data.put("adherenceRate", totalScheduled == 0 ? 100
                : Math.round(totalTaken * 100.0f / totalScheduled));
        data.put("extraRecords", extra);
        return data;
    }

    /** 服药历史（默认最近 7 天），按日期倒序，附带逐日汇总 */
    public Map<String, Object> history(String userId, int days, int page, int pageSize) {
        int d = Math.max(1, Math.min(days, 365));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(d - 1L);

        List<MedicineRecord> records = recordMapper.selectByUserAndRange(userId, from, to);

        List<Map<String, Object>> all = new ArrayList<>();
        for (MedicineRecord r : records) {
            all.add(toRecordView(r));
        }

        // 逐日汇总
        Map<String, Map<String, Integer>> byDate = new LinkedHashMap<>();
        for (MedicineRecord r : records) {
            String key = r.getTakenDate() == null ? "-" : r.getTakenDate().toString();
            Map<String, Integer> day = byDate.computeIfAbsent(key, k -> {
                Map<String, Integer> m = new LinkedHashMap<>();
                m.put("taken", 0);
                m.put("missed", 0);
                m.put("skipped", 0);
                m.put("total", 0);
                return m;
            });
            day.put("total", day.get("total") + 1);
            String st = r.getStatus() == null ? "taken" : r.getStatus();
            if (day.containsKey(st)) {
                day.put(st, day.get(st) + 1);
            }
        }

        List<Map<String, Object>> dailyStats = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> e : byDate.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", e.getKey());
            m.putAll(e.getValue());
            dailyStats.add(m);
        }
        dailyStats.sort((x, y) -> String.valueOf(y.get("date")).compareTo(String.valueOf(x.get("date"))));

        int p = Math.max(1, page);
        int size = Math.max(1, Math.min(pageSize, 200));
        int start = Math.min((p - 1) * size, all.size());
        int end = Math.min(start + size, all.size());
        long total = all.size();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", all.subList(start, end));
        data.put("total", total);
        data.put("page", p);
        data.put("pageSize", size);
        data.put("hasMore", (long) p * size < total);
        data.put("dailyStats", dailyStats);
        data.put("from", from.toString());
        data.put("to", to.toString());
        return data;
    }

    /**
     * 服药统计（依从率）
     *
     * 用 MedicineRecordMapper.adherenceStats 在数据库侧分类计数。
     */
    public Map<String, Object> stats(String userId, int days) {
        int d = Math.max(1, Math.min(days, 365));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(d - 1L);

        Map<String, Object> row = recordMapper.adherenceStats(userId, from, to);

        int taken = num(row, "taken");
        int missed = num(row, "missed");
        int skipped = num(row, "skipped");
        int total = num(row, "total");

        // 依从率 = 已服 / (已服 + 漏服)，跳过不计入分母
        int denominator = taken + missed;
        int adherenceRate = denominator == 0 ? 100 : Math.round(taken * 100.0f / denominator);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", d);
        data.put("totalReminders", total);
        data.put("takenCount", taken);
        data.put("missedCount", missed);
        data.put("skippedCount", skipped);
        data.put("pendingCount", Math.max(0, denominator - taken - missed));
        data.put("adherenceRate", adherenceRate);
        data.put("level", adherenceLevel(adherenceRate));
        data.put("suggestion", adherenceSuggestion(adherenceRate, missed));
        data.put("from", from.toString());
        data.put("to", to.toString());
        return data;
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

    /** 提醒转前端视图 */
    private Map<String, Object> toReminderView(MedicineReminder r, List<MedicineRecord> todayRecords) {
        List<String> times = parseTimes(r.getTimes());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("userId", r.getUserId());
        m.put("medicineName", r.getMedicineName());
        m.put("name", r.getMedicineName());          // 兼容旧字段名
        m.put("dosage", r.getDosage());
        m.put("frequency", r.getFrequency());
        m.put("frequencyText", frequencyText(r.getFrequency()));
        m.put("times", times);
        m.put("periods", times.stream().map(MedicineService::periodOfTime).distinct().toList());
        m.put("startDate", r.getStartDate() == null ? null : r.getStartDate().toString());
        m.put("endDate", r.getEndDate() == null ? null : r.getEndDate().toString());
        m.put("longTerm", r.getEndDate() == null);
        m.put("notes", r.getNotes());
        m.put("isActive", r.getIsActive() == null || r.getIsActive());
        m.put("createTime", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
        m.put("updateTime", r.getUpdatedAt() == null ? null : r.getUpdatedAt().toString());

        // 今天该提醒是否已完成（所有时段均已 taken）
        if (todayRecords != null && !todayRecords.isEmpty()) {
            long takenCount = todayRecords.stream()
                    .filter(rec -> Objects.equals(rec.getReminderId(), r.getId()))
                    .filter(rec -> "taken".equals(rec.getStatus()))
                    .count();
            m.put("todayTakenCount", takenCount);
            m.put("todayCompleted", !times.isEmpty() && takenCount >= times.size());
        } else {
            m.put("todayTakenCount", 0L);
            m.put("todayCompleted", false);
        }

        return m;
    }

    /** 服药记录转前端视图 */
    private Map<String, Object> toRecordView(MedicineRecord r) {
        String period = r.getTakenTime() == null ? null : periodOf(r.getTakenTime().getHour());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("userId", r.getUserId());
        m.put("reminderId", r.getReminderId());
        m.put("medicineName", r.getMedicineName());
        m.put("date", r.getTakenDate() == null ? null : r.getTakenDate().toString());
        m.put("takenDate", r.getTakenDate() == null ? null : r.getTakenDate().toString());
        m.put("time", r.getTakenTime() == null ? null
                : String.format("%02d:%02d", r.getTakenTime().getHour(), r.getTakenTime().getMinute()));
        m.put("takenTime", r.getTakenTime() == null ? null : r.getTakenTime().toString());
        m.put("status", r.getStatus());
        m.put("statusText", STATUS_TEXTS.getOrDefault(
                r.getStatus() == null ? "taken" : r.getStatus(), "未知"));
        m.put("period", period);
        m.put("periodText", period == null ? null : PERIOD_TEXTS.get(period));
        m.put("notes", r.getNotes());
        m.put("createTime", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
        return m;
    }

    /** times JSON 字符串转 List */
    private List<String> parseTimes(String times) {
        if (times == null || times.isBlank()) {
            return new ArrayList<>();
        }
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = JSONUtil.parseArray(times);
            for (Object o : arr) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    list.add(String.valueOf(o).trim());
                }
            }
        } catch (Exception e) {
            // 退化为逗号分隔
            for (String part : times.split("[,，]")) {
                if (!part.isBlank()) list.add(part.trim());
            }
        }
        return list;
    }

    private int countTimes(String timesJson) {
        return parseTimes(timesJson).size();
    }

    /** 归一化 times：数组 / 逗号分隔字符串 → JSON 数组字符串 */
    private String normalizeTimes(Object times) {
        if (times == null) return null;

        if (times instanceof List<?> list) {
            List<String> items = new ArrayList<>();
            for (Object o : list) {
                if (o != null && !String.valueOf(o).isBlank()) {
                    items.add(String.valueOf(o).trim());
                }
            }
            return items.isEmpty() ? null : JSONUtil.toJsonStr(items);
        }

        if (times instanceof Object[] arr) {
            return normalizeTimes(Arrays.asList(arr));
        }

        String s = String.valueOf(times).trim();
        if (s.isEmpty() || "null".equals(s)) return null;

        List<String> items = new ArrayList<>();
        for (String part : s.split("[,，]")) {
            if (!part.isBlank()) items.add(part.trim());
        }
        return items.isEmpty() ? null : JSONUtil.toJsonStr(items);
    }

    private LocalDate parseDate(Object o, LocalDate defaultValue) {
        String s = str(o);
        if (s == null) return defaultValue;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw BizException.badRequest("日期格式不正确，应为 yyyy-MM-dd");
        }
    }

    private LocalDate parseDateOrNull(Object o) {
        String s = str(o);
        if (s == null) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw BizException.badRequest("日期格式不正确，应为 yyyy-MM-dd");
        }
    }

    private String frequencyText(String frequency) {
        return switch (frequency == null ? "" : frequency) {
            case "daily" -> "每日一次";
            case "twice" -> "每日两次";
            case "three_times" -> "每日三次";
            case "weekly" -> "每周一次";
            case "as_needed" -> "按需服用";
            default -> "遵医嘱";
        };
    }

    private String adherenceLevel(int rate) {
        if (rate >= 90) return "优秀";
        if (rate >= 75) return "良好";
        if (rate >= 60) return "一般";
        return "需关注";
    }

    private String adherenceSuggestion(int rate, int missed) {
        if (missed == 0 && rate >= 90) {
            return "服药依从性很好，继续保持！";
        }
        if (rate >= 75) {
            return "整体依从性不错，注意按时服药";
        }
        if (rate >= 60) {
            return "有漏服情况，建议设置手机闹钟提醒";
        }
        return "漏服较多，建议请家属协助提醒，或使用分药盒按天备药";
    }

    private String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private int num(Map<String, Object> map, String key) {
        if (map == null) return 0;
        Object v = map.get(key);
        if (v == null) return 0;
        try {
            return (int) Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }
}
