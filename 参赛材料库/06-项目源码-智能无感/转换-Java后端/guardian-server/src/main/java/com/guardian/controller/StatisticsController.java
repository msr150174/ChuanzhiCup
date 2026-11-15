package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.ViewUtil;
import com.guardian.entity.*;
import com.guardian.mapper.*;
import com.guardian.service.AuthService;
import com.guardian.service.HealthScoreService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 综合统计接口
 *
 * 路由：
 *   GET /api/statistics/overview   综合概览（打卡/健康/运动/饮食/用药 五项汇总）
 *   GET /api/statistics/daily      日报
 *   GET /api/statistics/weekly     周报
 *   GET /api/statistics/monthly    月报
 *   GET /api/statistics/trend      趋势数据（图表用）
 *   GET /api/statistics/advice     健康建议
 *
 * ★ 复用 HealthScoreService.calculate(userId) 拿五项加权健康总分，
 *   其余维度直接查各业务表，全部走真实的 SQL 聚合。
 */
@Slf4j
@RestController
@RequestMapping("/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final CheckinRecordMapper checkinMapper;
    private final ExerciseRecordMapper exerciseMapper;
    private final DietRecordMapper dietMapper;
    private final MedicineRecordMapper medicineRecordMapper;
    private final AlertMapper alertMapper;
    private final AuthService authService;
    private final HealthScoreService healthScoreService;

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    // ========================================================================
    // ① 综合概览
    // ========================================================================

    /**
     * 综合概览
     *
     * GET /api/statistics/overview?userId=...
     *
     * 一次返回五大模块的关键指标 + 健康总分。
     */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(HttpServletRequest req,
                                                     @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));
        String uid = blankToNull(userId) == null ? me.getId() : userId;

        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", uid);
        data.put("date", today.toString());
        data.put("range", ViewUtil.map("from", weekAgo.toString(), "to", today.toString()));

        // ---------- ① 打卡 ----------
        List<CheckinRecord> checkins = checkinMapper.selectByUserAndRange(uid, weekAgo, today);
        CheckinRecord todayCheckin = checkinMapper.selectByUserAndDate(uid, today);

        Map<String, Object> checkinPart = new LinkedHashMap<>();
        checkinPart.put("weekDays", checkins.size());
        checkinPart.put("weekTarget", 7);
        checkinPart.put("continuousDays", calcContinuous(checkinMapper.selectRecentDates(uid), today));
        checkinPart.put("todayStatus", todayCheckin == null ? "none"
                : (todayCheckin.getWakeTime() != null ? "done"
                : (todayCheckin.getSleepTime() != null ? "sleeping" : "none")));
        checkinPart.put("todaySleepTime", todayCheckin == null ? null : todayCheckin.getSleepTime());
        checkinPart.put("todayWakeTime", todayCheckin == null ? null : todayCheckin.getWakeTime());
        checkinPart.put("avgSleepHours", ViewUtil.round(avgSleepHours(checkins), 1));
        data.put("checkin", checkinPart);

        // ---------- ② 健康评分 ----------
        Map<String, Object> healthPart = new LinkedHashMap<>();
        try {
            HealthScoreService.HealthScore s = healthScoreService.calculate(uid);
            healthPart.put("totalScore", s.getTotalScore());
            healthPart.put("level", s.getLevel());
            healthPart.put("suggestion", s.getSuggestion());
            healthPart.put("dimensions", ViewUtil.map(
                    "sleep", s.getSleepScore(),
                    "medicine", s.getMedicineScore(),
                    "exercise", s.getExerciseScore(),
                    "diet", s.getDietScore(),
                    "monitor", s.getMonitorScore()
            ));
        } catch (Exception e) {
            log.warn("健康评分计算失败: {}", e.getMessage());
            healthPart.put("totalScore", null);
            healthPart.put("level", "未知");
            healthPart.put("suggestion", "健康数据不足，暂时无法评分");
        }
        data.put("health", healthPart);

        // ---------- ③ 运动 ----------
        List<ExerciseRecord> exercises = exerciseMapper.selectByUserAndRange(uid, weekAgo, today);
        Map<String, Object> exSum = exerciseMapper.sumByRange(uid, weekAgo, today);

        long totalSteps = ViewUtil.toLong(exSum == null ? null : exSum.get("steps"), 0);
        long totalDuration = ViewUtil.toLong(exSum == null ? null : exSum.get("duration"), 0);
        long totalCalories = ViewUtil.toLong(exSum == null ? null : exSum.get("calories"), 0);

        Map<String, Object> exercisePart = new LinkedHashMap<>();
        exercisePart.put("weekSteps", totalSteps);
        exercisePart.put("weekDuration", totalDuration);
        exercisePart.put("weekCalories", totalCalories);
        exercisePart.put("weekDistance", ViewUtil.toDouble(exSum == null ? null : exSum.get("distance"), 0));
        exercisePart.put("avgSteps", exercises.isEmpty() ? 0 : totalSteps / 7);
        exercisePart.put("recordCount", exercises.size());
        exercisePart.put("days", exercises.stream().map(ExerciseRecord::getDate).distinct().count());
        data.put("exercise", exercisePart);

        // ---------- ④ 饮食 ----------
        List<DietRecord> diets = dietMapper.selectByUserAndRange(uid, weekAgo, today);
        Map<String, Object> dietSum = dietMapper.sumNutrition(uid, weekAgo, today);

        Map<LocalDate, Set<String>> mealsByDate = new HashMap<>();
        for (DietRecord d : diets) {
            mealsByDate.computeIfAbsent(d.getDate(), k -> new HashSet<>()).add(d.getMealType());
        }
        double avgMeals = mealsByDate.isEmpty() ? 0
                : mealsByDate.values().stream().mapToInt(Set::size).average().orElse(0);

        Map<String, Object> dietPart = new LinkedHashMap<>();
        dietPart.put("weekCalories", ViewUtil.toInt(dietSum == null ? null : dietSum.get("calories"), 0));
        dietPart.put("weekProtein", ViewUtil.toInt(dietSum == null ? null : dietSum.get("protein"), 0));
        dietPart.put("weekCarbs", ViewUtil.toInt(dietSum == null ? null : dietSum.get("carbs"), 0));
        dietPart.put("weekFat", ViewUtil.toInt(dietSum == null ? null : dietSum.get("fat"), 0));
        dietPart.put("avgMealsPerDay", ViewUtil.round(avgMeals, 1));
        dietPart.put("recordCount", diets.size());
        dietPart.put("recordedDays", mealsByDate.size());
        data.put("diet", dietPart);

        // ---------- ⑤ 用药 ----------
        Map<String, Object> medSum = medicineRecordMapper.adherenceStats(uid, weekAgo, today);
        long taken = ViewUtil.toLong(medSum == null ? null : medSum.get("taken"), 0);
        long missed = ViewUtil.toLong(medSum == null ? null : medSum.get("missed"), 0);
        long skipped = ViewUtil.toLong(medSum == null ? null : medSum.get("skipped"), 0);

        Map<String, Object> medicinePart = new LinkedHashMap<>();
        medicinePart.put("taken", taken);
        medicinePart.put("missed", missed);
        medicinePart.put("skipped", skipped);
        medicinePart.put("adherence", (taken + missed) == 0 ? 100
                : Math.round(taken * 100.0 / (taken + missed)));
        data.put("medicine", medicinePart);

        // ---------- 告警 ----------
        List<Alert> alerts = alertMapper.selectByUserAndRange(uid, weekAgo.atStartOfDay(), today.plusDays(1).atStartOfDay());
        Map<String, Object> alertPart = new LinkedHashMap<>();
        alertPart.put("weekTotal", alerts.size());
        alertPart.put("weekUnhandled", alerts.stream().filter(a -> Boolean.FALSE.equals(a.getHandled())).count());
        data.put("alert", alertPart);

        data.put("generatedAt", LocalDateTime.now().toString());
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // ② 日报 / 周报 / 月报
    // ========================================================================

    /** 日报（默认昨天，可传 date） */
    @GetMapping("/daily")
    public ApiResponse<Map<String, Object>> daily(HttpServletRequest req,
                                                  @RequestParam(required = false) String userId,
                                                  @RequestParam(required = false) String date) {
        User me = authService.requireUser(token(req));
        String uid = blankToNull(userId) == null ? me.getId() : userId;

        LocalDate d = ViewUtil.toDate(date);
        if (d == null) d = LocalDate.now();

        CheckinRecord checkin = checkinMapper.selectByUserAndDate(uid, d);
        List<ExerciseRecord> exercises = exerciseMapper.selectByUserAndDate(uid, d);
        List<DietRecord> diets = dietMapper.selectByUserAndDate(uid, d);
        List<MedicineRecord> medicines = medicineRecordMapper.selectByUserAndDate(uid, d);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", uid);
        data.put("date", d.toString());

        // 打卡
        Map<String, Object> ck = new LinkedHashMap<>();
        ck.put("sleepTime", checkin == null ? null : checkin.getSleepTime());
        ck.put("wakeTime", checkin == null ? null : checkin.getWakeTime());
        ck.put("sleepScore", checkin == null ? null : checkin.getSleepScore());
        ck.put("sleepHours", checkin == null ? null
                : ViewUtil.round(hoursBetween(checkin.getSleepTime(), checkin.getWakeTime()), 1));
        ck.put("checked", checkin != null);
        data.put("checkin", ck);

        // 运动
        long steps = exercises.stream().mapToLong(e -> e.getSteps() == null ? 0 : e.getSteps()).sum();
        long duration = exercises.stream().mapToLong(e -> e.getDuration() == null ? 0 : e.getDuration()).sum();
        long calories = exercises.stream().mapToLong(e -> e.getCalories() == null ? 0 : e.getCalories()).sum();
        data.put("exercise", ViewUtil.map(
                "steps", steps,
                "duration", duration,
                "calories", calories,
                "recordCount", exercises.size(),
                "types", exercises.stream().map(ExerciseRecord::getExerciseType).distinct().toList()
        ));

        // 饮食
        int dietCalories = diets.stream().mapToInt(r -> r.getCalories() == null ? 0 : r.getCalories()).sum();
        data.put("diet", ViewUtil.map(
                "calories", dietCalories,
                "meals", diets.stream().map(DietRecord::getMealType).distinct().toList(),
                "recordCount", diets.size()
        ));

        // 用药
        long takenCount = medicines.stream().filter(r -> "taken".equals(r.getStatus())).count();
        data.put("medicine", ViewUtil.map(
                "taken", takenCount,
                "missed", medicines.stream().filter(r -> "missed".equals(r.getStatus())).count(),
                "total", medicines.size(),
                "adherence", medicines.isEmpty() ? 100 : Math.round(takenCount * 100.0 / medicines.size())
        ));

        // 当日健康评分（五项加权，基于最近 7 天窗口）
        try {
            HealthScoreService.HealthScore s = healthScoreService.calculate(uid);
            data.put("totalScore", s.getTotalScore());
            data.put("level", s.getLevel());
        } catch (Exception e) {
            data.put("totalScore", null);
            data.put("level", "未知");
        }

        return ApiResponse.ok(data);
    }

    /** 周报（默认最近 7 天） */
    @GetMapping("/weekly")
    public ApiResponse<Map<String, Object>> weekly(HttpServletRequest req,
                                                   @RequestParam(required = false) String userId,
                                                   @RequestParam(required = false) String date) {
        User me = authService.requireUser(token(req));
        String uid = blankToNull(userId) == null ? me.getId() : userId;

        LocalDate end = ViewUtil.toDate(date);
        if (end == null) end = LocalDate.now();
        LocalDate start = end.minusDays(6);

        return ApiResponse.ok(rangeReport(uid, start, end, "week"));
    }

    /** 月报（默认本月） */
    @GetMapping("/monthly")
    public ApiResponse<Map<String, Object>> monthly(HttpServletRequest req,
                                                    @RequestParam(required = false) String userId,
                                                    @RequestParam(required = false) String month) {
        User me = authService.requireUser(token(req));
        String uid = blankToNull(userId) == null ? me.getId() : userId;

        LocalDate start;
        LocalDate end;

        String m = blankToNull(month);
        if (m != null && m.length() >= 7) {
            // 形如 "2026-01"
            try {
                start = LocalDate.parse(m.substring(0, 7) + "-01");
            } catch (Exception e) {
                start = LocalDate.now().withDayOfMonth(1);
            }
        } else {
            start = LocalDate.now().withDayOfMonth(1);
        }
        end = start.withDayOfMonth(start.lengthOfMonth());

        Map<String, Object> data = rangeReport(uid, start, end, "month");
        data.put("month", start.toString().substring(0, 7));
        return ApiResponse.ok(data);
    }

    /**
     * 区间报表（周报 / 月报共用）
     *
     * 汇总区间内的打卡、运动、饮食、用药、告警，并计算日均值。
     */
    private Map<String, Object> rangeReport(String uid, LocalDate start, LocalDate end, String kind) {
        // ★ 用 ChronoUnit 算天数差，比 getDayOfYear 相减可靠（跨年时不会算错）
        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
        if (days <= 0) days = 1;
        long effectiveDays = days;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", uid);
        data.put("kind", kind);
        data.put("from", start.toString());
        data.put("to", end.toString());
        data.put("days", effectiveDays);

        // ---------- 打卡 ----------
        List<CheckinRecord> checkins = checkinMapper.selectByUserAndRange(uid, start, end);
        double avgSleep = avgSleepHours(checkins);

        data.put("checkin", ViewUtil.map(
                "days", checkins.size(),
                "rate", Math.round(checkins.size() * 100.0 / effectiveDays),
                "avgSleepHours", ViewUtil.round(avgSleep, 1),
                "avgSleepScore", checkins.isEmpty() ? 0
                        : Math.round(checkins.stream()
                            .filter(c -> c.getSleepScore() != null)
                            .mapToInt(CheckinRecord::getSleepScore)
                            .average().orElse(0))
        ));

        // ---------- 运动 ----------
        Map<String, Object> exSum = exerciseMapper.sumByRange(uid, start, end);
        List<ExerciseRecord> exercises = exerciseMapper.selectByUserAndRange(uid, start, end);
        long steps = ViewUtil.toLong(exSum == null ? null : exSum.get("steps"), 0);
        long duration = ViewUtil.toLong(exSum == null ? null : exSum.get("duration"), 0);
        long calories = ViewUtil.toLong(exSum == null ? null : exSum.get("calories"), 0);

        data.put("exercise", ViewUtil.map(
                "totalSteps", steps,
                "totalDuration", duration,
                "totalCalories", calories,
                "totalDistance", ViewUtil.toDouble(exSum == null ? null : exSum.get("distance"), 0),
                "avgSteps", steps / effectiveDays,
                "recordCount", exercises.size(),
                "activeDays", exercises.stream().map(ExerciseRecord::getDate).distinct().count()
        ));

        // ---------- 饮食 ----------
        Map<String, Object> dietSum = dietMapper.sumNutrition(uid, start, end);
        List<DietRecord> diets = dietMapper.selectByUserAndRange(uid, start, end);
        int dietCalories = ViewUtil.toInt(dietSum == null ? null : dietSum.get("calories"), 0);

        data.put("diet", ViewUtil.map(
                "totalCalories", dietCalories,
                "avgCaloriesPerDay", dietCalories / effectiveDays,
                "totalProtein", ViewUtil.toInt(dietSum == null ? null : dietSum.get("protein"), 0),
                "totalCarbs", ViewUtil.toInt(dietSum == null ? null : dietSum.get("carbs"), 0),
                "totalFat", ViewUtil.toInt(dietSum == null ? null : dietSum.get("fat"), 0),
                "recordCount", diets.size()
        ));

        // ---------- 用药 ----------
        Map<String, Object> medSum = medicineRecordMapper.adherenceStats(uid, start, end);
        long taken = ViewUtil.toLong(medSum == null ? null : medSum.get("taken"), 0);
        long missed = ViewUtil.toLong(medSum == null ? null : medSum.get("missed"), 0);
        long skipped = ViewUtil.toLong(medSum == null ? null : medSum.get("skipped"), 0);

        data.put("medicine", ViewUtil.map(
                "taken", taken,
                "missed", missed,
                "skipped", skipped,
                "total", ViewUtil.toLong(medSum == null ? null : medSum.get("total"), 0),
                "adherence", (taken + missed) == 0 ? 100 : Math.round(taken * 100.0 / (taken + missed))
        ));

        // ---------- 告警 ----------
        List<Alert> alerts = alertMapper.selectByUserAndRange(
                uid, start.atStartOfDay(), end.plusDays(1).atStartOfDay());

        Map<String, Long> byType = new LinkedHashMap<>();
        for (Alert a : alerts) {
            byType.merge(a.getAlertType() == null ? "unknown" : a.getAlertType(), 1L, Long::sum);
        }

        data.put("alert", ViewUtil.map(
                "total", alerts.size(),
                "unhandled", alerts.stream().filter(a -> Boolean.FALSE.equals(a.getHandled())).count(),
                "byType", byType
        ));

        // ---------- 健康评分 ----------
        try {
            HealthScoreService.HealthScore s = healthScoreService.calculate(uid);
            data.put("healthScore", ViewUtil.map(
                    "totalScore", s.getTotalScore(),
                    "level", s.getLevel(),
                    "suggestion", s.getSuggestion()
            ));
        } catch (Exception e) {
            log.warn("区间报表健康评分计算失败: {}", e.getMessage());
            data.put("healthScore", null);
        }

        data.put("generatedAt", LocalDateTime.now().toString());
        return data;
    }

    // ========================================================================
    // ③ 趋势
    // ========================================================================

    /**
     * 趋势数据（图表用）
     *
     * GET /api/statistics/trend?userId=...&days=30&metric=steps
     *
     * metric: steps(步数) / sleep(睡眠时长) / calories(饮食热量) / weight(体重) / all(默认，全部返回)
     *
     * ★ 返回按日排列的数组，缺失日期补 0，方便前端直接画折线图。
     */
    @GetMapping("/trend")
    public ApiResponse<Map<String, Object>> trend(HttpServletRequest req,
                                                  @RequestParam(required = false) String userId,
                                                  @RequestParam(defaultValue = "7") int days,
                                                  @RequestParam(defaultValue = "all") String metric) {
        User me = authService.requireUser(token(req));
        String uid = blankToNull(userId) == null ? me.getId() : userId;

        int d = ViewUtil.clamp(days, 1, 180);
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(d - 1L);

        // ---------- 步数（运动表） ----------
        List<ExerciseRecord> exercises = exerciseMapper.selectByUserAndRange(uid, start, end);
        Map<LocalDate, Long> stepsByDate = new LinkedHashMap<>();
        Map<LocalDate, Long> exerciseMinutes = new LinkedHashMap<>();
        for (ExerciseRecord e : exercises) {
            if (e.getDate() == null) continue;
            long s = e.getSteps() == null ? 0 : e.getSteps();
            long dur = e.getDuration() == null ? 0 : e.getDuration();
            stepsByDate.merge(e.getDate(), s, Long::sum);
            exerciseMinutes.merge(e.getDate(), dur, Long::sum);
        }

        // ---------- 睡眠（打卡表） ----------
        List<CheckinRecord> checkins = checkinMapper.selectByUserAndRange(uid, start, end);
        Map<LocalDate, Double> sleepByDate = new LinkedHashMap<>();
        Map<LocalDate, Integer> sleepScoreByDate = new LinkedHashMap<>();
        for (CheckinRecord c : checkins) {
            if (c.getDate() == null) continue;
            double h = hoursBetween(c.getSleepTime(), c.getWakeTime());
            if (h > 0) sleepByDate.put(c.getDate(), ViewUtil.round(h, 1));
            if (c.getSleepScore() != null) sleepScoreByDate.put(c.getDate(), c.getSleepScore());
        }

        // ---------- 饮食热量 ----------
        List<DietRecord> diets = dietMapper.selectByUserAndRange(uid, start, end);
        Map<LocalDate, Integer> caloriesByDate = new LinkedHashMap<>();
        for (DietRecord r : diets) {
            if (r.getDate() == null) continue;
            caloriesByDate.merge(r.getDate(), r.getCalories() == null ? 0 : r.getCalories(), Integer::sum);
        }

        // ---------- 用药依从 ----------
        List<MedicineRecord> medicines = medicineRecordMapper.selectByUserAndRange(uid, start, end);
        Map<LocalDate, long[]> medByDate = new LinkedHashMap<>();
        for (MedicineRecord r : medicines) {
            if (r.getTakenDate() == null) continue;
            long[] pair = medByDate.computeIfAbsent(r.getTakenDate(), k -> new long[2]);
            if ("taken".equals(r.getStatus())) pair[0]++;
            else if ("missed".equals(r.getStatus())) pair[1]++;
        }

        // ---------- 组装每日序列（缺失补 0） ----------
        List<String> labels = new ArrayList<>();
        List<Long> stepsSeries = new ArrayList<>();
        List<Long> exerciseSeries = new ArrayList<>();
        List<Double> sleepSeries = new ArrayList<>();
        List<Integer> sleepScoreSeries = new ArrayList<>();
        List<Integer> caloriesSeries = new ArrayList<>();
        List<Integer> adherenceSeries = new ArrayList<>();

        boolean all = "all".equalsIgnoreCase(metric);
        boolean wantSteps = all || "steps".equalsIgnoreCase(metric);
        boolean wantSleep = all || "sleep".equalsIgnoreCase(metric);
        boolean wantCalories = all || "calories".equalsIgnoreCase(metric);

        for (LocalDate cur = start; !cur.isAfter(end); cur = cur.plusDays(1)) {
            labels.add(cur.toString());
            stepsSeries.add(wantSteps ? stepsByDate.getOrDefault(cur, 0L) : null);
            exerciseSeries.add(wantSteps ? exerciseMinutes.getOrDefault(cur, 0L) : null);
            sleepSeries.add(wantSleep ? sleepByDate.getOrDefault(cur, 0.0) : null);
            sleepScoreSeries.add(wantSleep ? sleepScoreByDate.getOrDefault(cur, 0) : null);
            caloriesSeries.add(wantCalories ? caloriesByDate.getOrDefault(cur, 0) : null);

            long[] pair = medByDate.get(cur);
            if (pair == null) {
                adherenceSeries.add(100);       // 无用药计划视为满分
            } else {
                long tot = pair[0] + pair[1];
                adherenceSeries.add(tot == 0 ? 100 : (int) Math.round(pair[0] * 100.0 / tot));
            }
        }

        Map<String, Object> series = new LinkedHashMap<>();
        if (wantSteps) {
            series.put("steps", stepsSeries);
            series.put("exerciseMinutes", exerciseSeries);
        }
        if (wantSleep) {
            series.put("sleepHours", sleepSeries);
            series.put("sleepScore", sleepScoreSeries);
        }
        if (wantCalories) {
            series.put("calories", caloriesSeries);
        }
        series.put("medicineAdherence", adherenceSeries);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", uid);
        data.put("from", start.toString());
        data.put("to", end.toString());
        data.put("days", d);
        data.put("metric", metric);
        data.put("labels", labels);
        data.put("series", series);

        // 汇总值，便于前端显示
        data.put("summary", ViewUtil.map(
                "totalSteps", stepsSeries.stream().filter(Objects::nonNull).mapToLong(Long::longValue).sum(),
                "avgSteps", ViewUtil.round(stepsSeries.stream().filter(Objects::nonNull)
                        .mapToLong(Long::longValue).average().orElse(0), 0),
                "avgSleepHours", ViewUtil.round(sleepSeries.stream().filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue).average().orElse(0), 1),
                "avgCalories", ViewUtil.round(caloriesSeries.stream().filter(Objects::nonNull)
                        .mapToInt(Integer::intValue).average().orElse(0), 0),
                "avgAdherence", ViewUtil.round(adherenceSeries.stream().filter(Objects::nonNull)
                        .mapToInt(Integer::intValue).average().orElse(0), 0)
        ));

        return ApiResponse.ok(data);
    }

    // ========================================================================
    // ④ 健康建议
    // ========================================================================

    /**
     * 健康建议
     *
     * GET /api/statistics/advice?userId=...
     *
     * 纯规则生成：基于健康评分各维度得分给出可执行建议，不调用 AI（保证稳定且零延迟）。
     */
    @GetMapping("/advice")
    public ApiResponse<Map<String, Object>> advice(HttpServletRequest req,
                                                   @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));
        String uid = blankToNull(userId) == null ? me.getId() : userId;

        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);

        List<Map<String, Object>> advices = new ArrayList<>();

        HealthScoreService.HealthScore s = null;
        try {
            s = healthScoreService.calculate(uid);
        } catch (Exception e) {
            log.warn("健康建议：评分计算失败 {}", e.getMessage());
        }

        if (s != null) {
            if (s.getSleepScore() != null && s.getSleepScore() < 75) {
                advices.add(tip("sleep", "作息", "high",
                        "作息不太规律，建议固定睡觉和起床时间（22:00 睡 / 6:30 起）",
                        s.getSleepScore()));
            }
            if (s.getMedicineScore() != null && s.getMedicineScore() < 85) {
                advices.add(tip("medicine", "用药", "high",
                        "有漏服药物的情况，建议把药盒放在显眼处并开启用药提醒",
                        s.getMedicineScore()));
            }
            if (s.getExerciseScore() != null && s.getExerciseScore() < 70) {
                advices.add(tip("exercise", "运动", "medium",
                        "运动量偏少，建议每天散步 30 分钟，可分早晚两次进行",
                        s.getExerciseScore()));
            }
            if (s.getDietScore() != null && s.getDietScore() < 70) {
                advices.add(tip("diet", "饮食", "medium",
                        "饮食记录不完整，建议三餐规律并随手记录，注意少油少盐",
                        s.getDietScore()));
            }
            if (s.getMonitorScore() != null && s.getMonitorScore() < 80) {
                advices.add(tip("monitor", "监测", "high",
                        "近期有健康异常提醒，注意休息，必要时联系家庭医生",
                        s.getMonitorScore()));
            }
        }

        // 运动细节：日均步数
        List<ExerciseRecord> exercises = exerciseMapper.selectByUserAndRange(uid, weekAgo, today);
        long steps = exercises.stream().mapToLong(e -> e.getSteps() == null ? 0 : e.getSteps()).sum();
        long avgSteps = steps / 7;
        if (avgSteps < 3000) {
            advices.add(tip("steps", "步数", "medium",
                    String.format("近 7 天日均仅 %d 步，建议增加到 6000 步以上", avgSteps), null));
        } else if (avgSteps >= 10000) {
            advices.add(tip("steps", "步数", "info",
                    String.format("近 7 天日均 %d 步，运动量充足，继续保持 👍", avgSteps), null));
        }

        // 睡眠细节
        double avgSleep = avgSleepHours(checkinMapper.selectByUserAndRange(uid, weekAgo, today));
        if (avgSleep > 0 && avgSleep < 6) {
            advices.add(tip("sleepHours", "睡眠时长", "high",
                    String.format("平均每天只睡 %.1f 小时，明显不足，建议提前上床", avgSleep), null));
        } else if (avgSleep > 9.5) {
            advices.add(tip("sleepHours", "睡眠时长", "low",
                    String.format("平均每天睡 %.1f 小时，偏长，建议白天多活动", avgSleep), null));
        }

        // 打卡连续性
        int continuous = calcContinuous(checkinMapper.selectRecentDates(uid), today);
        if (continuous >= 7) {
            advices.add(tip("checkin", "打卡", "info",
                    String.format("已连续打卡 %d 天，非常棒！🎉", continuous), null));
        } else if (continuous == 0) {
            advices.add(tip("checkin", "打卡", "medium",
                    "今天还没有打卡，记得睡前点一下「睡觉打卡」哦", null));
        }

        if (advices.isEmpty()) {
            advices.add(tip("all", "综合", "info",
                    "各项指标都很棒，保持规律作息和适度运动，继续加油！🎉", null));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", uid);
        data.put("date", today.toString());
        data.put("totalScore", s == null ? null : s.getTotalScore());
        data.put("level", s == null ? "未知" : s.getLevel());
        data.put("summary", s == null ? "健康数据不足，暂时无法评分" : s.getSuggestion());
        data.put("advices", advices);
        data.put("count", advices.size());
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // 工具
    // ========================================================================

    private Map<String, Object> tip(String key, String category, String level,
                                    String content, Integer score) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("category", category);
        m.put("level", level);
        m.put("content", content);
        m.put("score", score);
        return m;
    }

    /**
     * 连续打卡天数
     *
     * 今天没打卡时从昨天开始算，遇到断档即停止。
     */
    private int calcContinuous(List<LocalDate> dates, LocalDate today) {
        if (dates == null || dates.isEmpty()) return 0;

        Set<LocalDate> set = new HashSet<>(dates);
        LocalDate cursor = set.contains(today) ? today : today.minusDays(1);
        if (!set.contains(cursor)) return 0;

        int count = 0;
        while (set.contains(cursor)) {
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }

    /** 平均睡眠时长（只统计同时有入睡和起床时间的记录） */
    private double avgSleepHours(List<CheckinRecord> checkins) {
        if (checkins == null || checkins.isEmpty()) return 0;
        return checkins.stream()
                .filter(c -> c.getSleepTime() != null && c.getWakeTime() != null)
                .mapToDouble(c -> hoursBetween(c.getSleepTime(), c.getWakeTime()))
                .filter(h -> h > 0 && h < 24)
                .average()
                .orElse(0);
    }

    /** "23:10" → "06:40" 的小时数（跨天按 +24 计） */
    private double hoursBetween(String sleepTime, String wakeTime) {
        if (sleepTime == null || wakeTime == null) return 0;
        try {
            String[] s = sleepTime.split(":");
            String[] w = wakeTime.split(":");
            int sleepMin = Integer.parseInt(s[0]) * 60 + Integer.parseInt(s[1]);
            int wakeMin = Integer.parseInt(w[0]) * 60 + Integer.parseInt(w[1]);
            if (wakeMin < sleepMin) wakeMin += 24 * 60;
            return (wakeMin - sleepMin) / 60.0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
