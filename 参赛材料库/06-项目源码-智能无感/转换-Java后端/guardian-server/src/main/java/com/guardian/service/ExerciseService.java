package com.guardian.service;

import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.entity.ExerciseRecord;
import com.guardian.mapper.ExerciseRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运动服务
 *
 * 负责运动记录、周 / 月汇总、类型分布与运动目标。
 *
 * ★ 关于「运动目标」的说明：
 *   数据库当前没有 exercise_goals 表（V1__init_schema.sql 中不存在），
 *   因此目标不做持久化，只在进程内存里按 userId 暂存（会话级），
 *   接口返回时会明确告知前端 configured 与 persisted 状态，
 *   不会假装已经写入数据库。后续如建表，只需替换 goals 的读写实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseRecordMapper exerciseMapper;

    /** 会话级运动目标缓存（进程重启即丢失，属预期行为） */
    private final Map<String, Map<String, Object>> goalStore = new ConcurrentHashMap<>();

    /** 默认目标：步数 8000 / 时长 30 分钟 / 卡路里 300 */
    private static final int DEFAULT_DAILY_STEPS = 8000;
    private static final int DEFAULT_DAILY_DURATION = 30;
    private static final int DEFAULT_DAILY_CALORIES = 300;

    private Integer toInt(Object o) {
        if (o == null) return null;
        try {
            return (int) Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private Double toDouble(Object o) {
        if (o == null) return null;
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }

    private static String typeText(String type) {
        return switch (type == null ? "" : type) {
            case "walk" -> "散步";
            case "run" -> "跑步";
            case "taichi" -> "太极";
            case "cycling" -> "骑行";
            case "swim" -> "游泳";
            case "dance" -> "广场舞";
            case "yoga" -> "瑜伽";
            case "climb" -> "爬山";
            default -> "其他运动";
        };
    }

    private static String intensityText(String intensity) {
        return switch (intensity == null ? "" : intensity) {
            case "low" -> "低强度";
            case "high" -> "高强度";
            default -> "中等强度";
        };
    }

    // ========================================================================
    // 记录
    // ========================================================================

    /**
     * 新增运动记录
     *
     * 请求体：{ "exerciseType": "walk", "duration": 30, "steps": 3000,
     *          "calories": 120, "distance": 2.1, "intensity": "moderate",
     *          "date": "2026-02-14", "notes": "" }
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> addRecord(String userId, Map<String, Object> body) {
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }

        // 兼容 exerciseType / type
        String type = str(body.getOrDefault("exerciseType", body.get("type")));
        if (type == null) {
            throw BizException.badRequest("请选择运动类型");
        }

        Integer duration = toInt(body.get("duration"));
        if (duration == null || duration <= 0) {
            throw BizException.badRequest("运动时长必须大于 0 分钟");
        }
        if (duration > 1440) {
            throw BizException.badRequest("运动时长不合理，请检查");
        }

        Integer steps = toInt(body.get("steps"));
        Integer calories = toInt(body.get("calories"));
        Double distance = toDouble(body.get("distance"));

        // 未填卡路里时按运动类型与时长估算，避免统计出现空洞
        if (calories == null || calories <= 0) {
            calories = estimateCalories(type, duration);
        }
        // 步行 / 跑步未填步数时按距离或时长估算
        if ((steps == null || steps <= 0) && ("walk".equals(type) || "run".equals(type))) {
            steps = estimateSteps(type, duration, distance);
        }

        ExerciseRecord r = new ExerciseRecord();
        r.setId(CommonUtil.uuid());
        r.setUserId(userId);
        r.setDate(parseDate(body.get("date")));
        r.setExerciseType(type);
        r.setDuration(duration);
        r.setSteps(steps == null ? 0 : steps);
        r.setCalories(calories);
        r.setDistance(distance == null ? BigDecimal.ZERO : BigDecimal.valueOf(distance));
        r.setIntensity(str(body.get("intensity")) == null ? "moderate" : str(body.get("intensity")));
        r.setNotes(str(body.get("notes")));

        exerciseMapper.insert(r);
        log.info("用户 {} 新增运动记录 {}（{}，{} 分钟）", userId, r.getId(), type, duration);

        Map<String, Object> view = toView(r);
        view.put("estimatedCalories", body.get("calories") == null);
        view.put("estimatedSteps", body.get("steps") == null);
        return view;
    }

    // ========================================================================
    // 查询
    // ========================================================================

    /** 今日运动：记录列表 + 汇总 */
    public Map<String, Object> today(String userId) {
        LocalDate today = LocalDate.now();
        List<ExerciseRecord> records = exerciseMapper.selectByUserAndDate(userId, today);

        List<Map<String, Object>> list = new ArrayList<>();
        int totalSteps = 0, totalDuration = 0, totalCalories = 0;
        double totalDistance = 0;
        for (ExerciseRecord r : records) {
            list.add(toView(r));
            totalSteps += nz(r.getSteps());
            totalDuration += nz(r.getDuration());
            totalCalories += nz(r.getCalories());
            totalDistance += r.getDistance() == null ? 0 : r.getDistance().doubleValue();
        }

        // 目标完成度
        Map<String, Object> goals = currentGoals(userId);
        int targetSteps = (int) goals.get("dailySteps");
        int targetDuration = (int) goals.get("dailyDuration");
        int targetCalories = (int) goals.get("dailyCalories");

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("steps", totalSteps);
        progress.put("stepsRate", rate(totalSteps, targetSteps));
        progress.put("duration", totalDuration);
        progress.put("durationRate", rate(totalDuration, targetDuration));
        progress.put("calories", totalCalories);
        progress.put("caloriesRate", rate(totalCalories, targetCalories));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", today.toString());
        data.put("records", list);
        data.put("total", list.size());
        data.put("totalSteps", totalSteps);
        data.put("totalDuration", totalDuration);
        data.put("totalCalories", totalCalories);
        data.put("totalDistance", round1(totalDistance));
        data.put("exerciseTypes", records.stream().map(ExerciseRecord::getExerciseType).distinct().toList());
        data.put("targets", new LinkedHashMap<>(Map.of(
                "steps", targetSteps, "duration", targetDuration, "calories", targetCalories)));
        data.put("progress", progress);
        return data;
    }

    /** 区间历史记录（默认最近 7 天），支持分页 */
    public Map<String, Object> history(String userId, int days, int page, int pageSize) {
        int d = Math.max(1, Math.min(days, 365));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(d - 1L);

        List<ExerciseRecord> records = exerciseMapper.selectByUserAndRange(userId, from, to);

        List<Map<String, Object>> all = new ArrayList<>();
        for (ExerciseRecord r : records) {
            all.add(toView(r));
        }

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
        data.put("from", from.toString());
        data.put("to", to.toString());
        return data;
    }

    /**
     * 近 7 天逐日汇总
     *
     * 没有运动的日期补 0，保证前端折线图连续。
     */
    public Map<String, Object> weekly(String userId) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);

        List<ExerciseRecord> records = exerciseMapper.selectByUserAndRange(userId, from, today);

        Map<LocalDate, List<ExerciseRecord>> byDate = new HashMap<>();
        for (ExerciseRecord r : records) {
            byDate.computeIfAbsent(r.getDate(), k -> new ArrayList<>()).add(r);
        }

        List<Map<String, Object>> weekData = new ArrayList<>();
        int totalSteps = 0, totalDuration = 0, totalCalories = 0, exerciseDays = 0;

        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            List<ExerciseRecord> dayRecords = byDate.getOrDefault(date, List.of());

            int steps = dayRecords.stream().mapToInt(r -> nz(r.getSteps())).sum();
            int duration = dayRecords.stream().mapToInt(r -> nz(r.getDuration())).sum();
            int calories = dayRecords.stream().mapToInt(r -> nz(r.getCalories())).sum();

            totalSteps += steps;
            totalDuration += duration;
            totalCalories += calories;
            if (!dayRecords.isEmpty()) exerciseDays++;

            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", date.toString());
            day.put("weekday", weekdayText(date));
            day.put("steps", steps);
            day.put("duration", duration);
            day.put("calories", calories);
            day.put("count", dayRecords.size());
            weekData.add(day);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("weekData", weekData);
        data.put("totalSteps", totalSteps);
        data.put("totalDuration", totalDuration);
        data.put("totalCalories", totalCalories);
        data.put("exerciseDays", exerciseDays);
        data.put("avgStepsPerDay", Math.round(totalSteps / 7.0));
        data.put("avgDurationPerDay", Math.round(totalDuration / 7.0));
        data.put("avgCaloriesPerDay", Math.round(totalCalories / 7.0));
        data.put("from", from.toString());
        data.put("to", today.toString());
        return data;
    }

    /** 近 30 天汇总 */
    public Map<String, Object> monthly(String userId) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(29);

        Map<String, Object> sum = exerciseMapper.sumByRange(userId, from, today);
        int exerciseDays = exerciseMapper.countDays(userId, from, today);

        int steps = num(sum, "steps");
        int duration = num(sum, "duration");
        int calories = num(sum, "calories");
        double distance = numDouble(sum, "distance");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("from", from.toString());
        data.put("to", today.toString());
        data.put("days", 30);
        data.put("totalSteps", steps);
        data.put("totalDuration", duration);
        data.put("totalCalories", calories);
        data.put("totalDistance", round1(distance));
        data.put("exerciseDays", exerciseDays);
        data.put("avgStepsPerDay", Math.round(steps / 30.0));
        data.put("avgDurationPerDay", Math.round(duration / 30.0));
        data.put("avgCaloriesPerDay", Math.round(calories / 30.0));
        data.put("exerciseRate", Math.round(exerciseDays * 100.0 / 30));
        return data;
    }

    /**
     * 运动类型分布
     *
     * 默认统计近 30 天，按总时长降序。
     */
    public Map<String, Object> typeStats(String userId, int days) {
        int d = Math.max(1, Math.min(days, 365));
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(d - 1L);

        List<Map<String, Object>> rows = exerciseMapper.statsByType(userId, from, today);

        int grandDuration = rows.stream().mapToInt(m -> num(m, "duration")).sum();

        List<Map<String, Object>> types = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String type = row.get("exerciseType") == null ? null : String.valueOf(row.get("exerciseType"));
            int count = num(row, "count");
            int duration = num(row, "duration");

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", type);
            m.put("typeText", typeText(type));
            m.put("count", count);
            m.put("totalDuration", duration);
            m.put("totalSteps", num(row, "steps"));
            m.put("totalCalories", num(row, "calories"));
            m.put("totalDistance", round1(numDouble(row, "distance")));
            m.put("avgDuration", count == 0 ? 0 : Math.round(duration / (float) count));
            m.put("avgCalories", count == 0 ? 0 : Math.round(num(row, "calories") / (float) count));
            m.put("percent", grandDuration == 0 ? 0 : Math.round(duration * 100.0 / grandDuration));
            types.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("types", types);
        data.put("total", types.size());
        data.put("days", d);
        data.put("from", from.toString());
        data.put("to", today.toString());
        return data;
    }

    /**
     * 运动统计总览
     *
     * 用 ExerciseRecordMapper.sumByRange 在数据库侧求和。
     */
    public Map<String, Object> stats(String userId, int days) {
        int d = Math.max(1, Math.min(days, 365));
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(d - 1L);

        Map<String, Object> sum = exerciseMapper.sumByRange(userId, from, today);
        int exerciseDays = exerciseMapper.countDays(userId, from, today);

        int steps = num(sum, "steps");
        int duration = num(sum, "duration");
        int calories = num(sum, "calories");
        double distance = numDouble(sum, "distance");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", d);
        data.put("totalSteps", steps);
        data.put("totalDuration", duration);
        data.put("totalCalories", calories);
        data.put("totalDistance", round1(distance));
        data.put("exerciseDays", exerciseDays);
        data.put("exerciseRate", Math.round(exerciseDays * 100.0 / d));
        data.put("averageSteps", Math.round(steps / (double) d));
        data.put("averageDuration", Math.round(duration / (double) d));
        data.put("averageCalories", Math.round(calories / (double) d));
        data.put("avgStepsPerActiveDay", exerciseDays == 0 ? 0 : Math.round(steps / (double) exerciseDays));
        data.put("from", from.toString());
        data.put("to", today.toString());
        return data;
    }

    // ========================================================================
    // 运动目标（会话级，未持久化）
    // ========================================================================

    /**
     * 读取运动目标
     *
     * 数据库无 exercise_goals 表，返回默认目标 + 当日进度。
     * persisted=false 明确表示尚未落库。
     */
    public Map<String, Object> getGoals(String userId) {
        Map<String, Object> goals = currentGoals(userId);

        // 附上今日进度
        LocalDate today = LocalDate.now();
        List<ExerciseRecord> records = exerciseMapper.selectByUserAndDate(userId, today);

        int steps = records.stream().mapToInt(r -> nz(r.getSteps())).sum();
        int duration = records.stream().mapToInt(r -> nz(r.getDuration())).sum();
        int calories = records.stream().mapToInt(r -> nz(r.getCalories())).sum();

        int targetSteps = (int) goals.get("dailySteps");
        int targetDuration = (int) goals.get("dailyDuration");
        int targetCalories = (int) goals.get("dailyCalories");

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("steps", steps);
        progress.put("stepsRate", rate(steps, targetSteps));
        progress.put("duration", duration);
        progress.put("durationRate", rate(duration, targetDuration));
        progress.put("calories", calories);
        progress.put("caloriesRate", rate(calories, targetCalories));

        Map<String, Object> data = new LinkedHashMap<>(goals);
        data.put("userId", userId);
        data.put("date", today.toString());
        data.put("currentProgress", progress);
        return data;
    }

    /**
     * 设置运动目标
     *
     * ★ 数据库没有对应表，这里只在内存中暂存（会话级），
     *   返回信息中明确说明未持久化，不谎称已写库。
     */
    public Map<String, Object> setGoals(String userId, Map<String, Object> goals) {
        if (goals == null) {
            throw BizException.badRequest("请求体为空");
        }

        // 兼容 { "goals": {...} } 与平铺两种写法
        Object nested = goals.get("goals");
        Map<String, Object> src = goals;
        if (nested instanceof Map<?, ?> m) {
            src = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                src.put(String.valueOf(e.getKey()), e.getValue());
            }
        }

        Map<String, Object> current = currentGoals(userId);

        Integer steps = toInt(firstNonNull(src.get("dailySteps"), src.get("steps")));
        Integer duration = toInt(firstNonNull(src.get("dailyDuration"), src.get("duration")));
        Integer calories = toInt(firstNonNull(src.get("dailyCalories"), src.get("calories")));
        Integer weeklyDays = toInt(firstNonNull(src.get("weeklyExerciseDays"), src.get("weeklyDays")));

        int newSteps = steps == null ? (int) current.get("dailySteps") : steps;
        int newDuration = duration == null ? (int) current.get("dailyDuration") : duration;
        int newCalories = calories == null ? (int) current.get("dailyCalories") : calories;

        if (newSteps < 0 || newSteps > 100000) {
            throw BizException.badRequest("每日步数目标需在 0 ~ 100000 之间");
        }
        if (newDuration < 0 || newDuration > 600) {
            throw BizException.badRequest("每日时长目标需在 0 ~ 600 分钟之间");
        }
        if (newCalories < 0 || newCalories > 5000) {
            throw BizException.badRequest("每日热量目标需在 0 ~ 5000 kcal 之间");
        }

        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("dailySteps", newSteps);
        saved.put("dailyDuration", newDuration);
        saved.put("dailyCalories", newCalories);
        saved.put("weeklyExerciseDays", weeklyDays == null
                ? current.get("weeklyExerciseDays") : weeklyDays);
        saved.put("persisted", false);

        goalStore.put(userId, saved);
        log.info("用户 {} 设置运动目标（会话级）: 步数={}, 时长={}, 热量={}",
                userId, newSteps, newDuration, newCalories);

        Map<String, Object> data = new LinkedHashMap<>(saved);
        data.put("userId", userId);
        data.put("saved", true);
        data.put("message", "目标设置已保存（当前为会话级，后续可持久化）");
        return data;
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

    /** 取当前目标，未设置过时返回默认目标 */
    private Map<String, Object> currentGoals(String userId) {
        Map<String, Object> stored = goalStore.get(userId);
        Map<String, Object> goals = new LinkedHashMap<>();

        goals.put("dailySteps", stored == null
                ? DEFAULT_DAILY_STEPS : (int) stored.getOrDefault("dailySteps", DEFAULT_DAILY_STEPS));
        goals.put("dailyDuration", stored == null
                ? DEFAULT_DAILY_DURATION : (int) stored.getOrDefault("dailyDuration", DEFAULT_DAILY_DURATION));
        goals.put("dailyCalories", stored == null
                ? DEFAULT_DAILY_CALORIES : (int) stored.getOrDefault("dailyCalories", DEFAULT_DAILY_CALORIES));
        goals.put("weeklyExerciseDays", stored == null
                ? 5 : (int) stored.getOrDefault("weeklyExerciseDays", 5));
        goals.put("persisted", stored != null && Boolean.TRUE.equals(stored.get("persisted")));
        return goals;
    }

    /** 按运动类型估算热量（kcal） */
    private int estimateCalories(String type, int duration) {
        // 每分钟消耗参考值（老年人偏低强度）
        double perMinute = switch (type == null ? "" : type) {
            case "run" -> 9.0;
            case "cycling" -> 6.0;
            case "swim" -> 8.0;
            case "dance" -> 5.5;
            case "climb" -> 7.0;
            case "taichi" -> 3.0;
            case "yoga" -> 3.0;
            case "walk" -> 3.5;
            default -> 4.0;
        };
        return (int) Math.round(perMinute * duration);
    }

    /** 估算步数 */
    private int estimateSteps(String type, int duration, Double distanceKm) {
        // 跑步步幅约 1.0 m，步行约 0.7 m
        double stride = "run".equals(type) ? 1.0 : 0.7;
        if (distanceKm != null && distanceKm > 0) {
            return (int) Math.round(distanceKm * 1000 / stride);
        }
        // 步行约 90 步/分钟，跑步约 150 步/分钟
        int perMinute = "run".equals(type) ? 150 : 90;
        return perMinute * duration;
    }

    /** 记录转前端视图（camelCase） */
    private Map<String, Object> toView(ExerciseRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("userId", r.getUserId());
        m.put("date", r.getDate() == null ? null : r.getDate().toString());
        m.put("exerciseType", r.getExerciseType());
        m.put("typeText", typeText(r.getExerciseType()));
        m.put("duration", nz(r.getDuration()));
        m.put("steps", nz(r.getSteps()));
        m.put("calories", nz(r.getCalories()));
        m.put("distance", r.getDistance() == null ? 0 : r.getDistance().doubleValue());
        m.put("intensity", r.getIntensity());
        m.put("intensityText", intensityText(r.getIntensity()));
        m.put("notes", r.getNotes());
        m.put("createTime", r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
        return m;
    }

    private LocalDate parseDate(Object o) {
        String s = str(o);
        if (s == null) return LocalDate.now();
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            throw BizException.badRequest("日期格式不正确，应为 yyyy-MM-dd");
        }
    }

    private Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private String str(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim();
        return s.isEmpty() || "null".equals(s) ? null : s;
    }

    private int nz(Integer i) {
        return i == null ? 0 : i;
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

    private double numDouble(Map<String, Object> map, String key) {
        if (map == null) return 0;
        Object v = map.get(key);
        if (v == null) return 0;
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return 0;
        }
    }

    /** 完成率（0~100+，上限 999 防止异常值） */
    private int rate(int value, int target) {
        if (target <= 0) return 0;
        return Math.min(999, Math.round(value * 100.0f / target));
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private String weekdayText(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }
}
