package com.guardian.service;

import com.guardian.entity.*;
import com.guardian.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 健康评分服务
 *
 * 五项加权总分 100：
 *   作息规律 30% + 用药依从 25% + 运动量 20% + 饮食均衡 15% + 监测异常 10%
 *
 * 评分区间：
 *   90-100 优秀 | 75-89 良好 | 60-74 一般 | 0-59 需关注
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthScoreService {

    private final CheckinRecordMapper checkinMapper;
    private final MedicineRecordMapper medicineRecordMapper;
    private final ExerciseRecordMapper exerciseMapper;
    private final DietRecordMapper dietMapper;
    private final AlertMapper alertMapper;

    @Value("${guardian.health-score.weight.sleep:30}")
    private int wSleep;
    @Value("${guardian.health-score.weight.medicine:25}")
    private int wMedicine;
    @Value("${guardian.health-score.weight.exercise:20}")
    private int wExercise;
    @Value("${guardian.health-score.weight.diet:15}")
    private int wDiet;
    @Value("${guardian.health-score.weight.monitor:10}")
    private int wMonitor;

    @Value("${guardian.health-score.sleep.optimal-min:7}")
    private int sleepOptimalMin;
    @Value("${guardian.health-score.sleep.optimal-max:9}")
    private int sleepOptimalMax;
    @Value("${guardian.health-score.sleep.acceptable-min:6}")
    private int sleepAcceptableMin;

    /**
     * 计算用户健康总分（0-100）
     */
    public HealthScore calculate(String userId) {
        LocalDate today = LocalDate.now();
        LocalDate weekAgo = today.minusDays(6);

        HealthScore score = new HealthScore();
        score.setUserId(userId);
        score.setDate(today);

        // ---------- ① 作息规律（30分） ----------
        List<CheckinRecord> checkins = checkinMapper.selectByUserAndRange(userId, weekAgo, today);
        int sleepScore = calcSleepScore(checkins);
        score.setSleepScore(sleepScore);

        // ---------- ② 用药依从（25分） ----------
        int medicineScore = calcMedicineScore(userId, weekAgo, today);
        score.setMedicineScore(medicineScore);

        // ---------- ③ 运动量（20分） ----------
        List<ExerciseRecord> exercises = exerciseMapper.selectByUserAndRange(userId, weekAgo, today);
        int exerciseScore = calcExerciseScore(exercises);
        score.setExerciseScore(exerciseScore);

        // ---------- ④ 饮食均衡（15分） ----------
        List<DietRecord> diets = dietMapper.selectByUserAndRange(userId, weekAgo, today);
        int dietScore = calcDietScore(diets);
        score.setDietScore(dietScore);

        // ---------- ⑤ 监测异常（10分） ----------
        List<Alert> alerts = alertMapper.selectByUserAndRange(userId, weekAgo.atStartOfDay(), today.plusDays(1).atStartOfDay());
        int monitorScore = calcMonitorScore(alerts);
        score.setMonitorScore(monitorScore);

        // ---------- 加权总分 ----------
        int total = weighted(sleepScore, wSleep)
                + weighted(medicineScore, wMedicine)
                + weighted(exerciseScore, wExercise)
                + weighted(dietScore, wDiet)
                + weighted(monitorScore, wMonitor);

        score.setTotalScore(Math.max(0, Math.min(100, total)));
        score.setLevel(levelOf(score.getTotalScore()));
        score.setSuggestion(suggestionOf(score));

        return score;
    }

    // ========================================================================
    // 各维度计算（各返回 0-100，再按权重折算）
    // ========================================================================

    /**
     * 作息评分
     *
     * 规则：统计近 7 天打卡天数，并对睡眠时长评分。
     *   打卡率 60% + 平均睡眠时长 40%
     */
    private int calcSleepScore(List<CheckinRecord> checkins) {
        if (checkins == null || checkins.isEmpty()) {
            return 0;
        }

        // 打卡率：7 天中有几天有记录
        long days = checkins.stream().map(CheckinRecord::getDate).distinct().count();
        int rateScore = (int) Math.round(days / 7.0 * 100);

        // 平均睡眠时长
        double avgHours = checkins.stream()
                .filter(c -> c.getSleepTime() != null && c.getWakeTime() != null)
                .mapToDouble(c -> hoursBetween(c.getSleepTime(), c.getWakeTime()))
                .filter(h -> h > 0 && h < 24)
                .average()
                .orElse(0);

        if (avgHours == 0) {
            return rateScore;   // 没有时长数据时只看打卡率
        }

        int durationScore = sleepDurationScore(avgHours);
        return (int) Math.round(rateScore * 0.6 + durationScore * 0.4);
    }

    /**
     * 睡眠时长评分（移植原项目算法）
     *
     * 7-9 小时最优 → 95-100 分
     * 6-7 小时一般 → 70-90 分
     * >9 小时过长  → 80 分
     * <6 小时不足  → 最多 50 分
     */
    public int sleepDurationScore(double hours) {
        if (hours >= sleepOptimalMin && hours <= sleepOptimalMax) {
            return (int) Math.min(95 + (hours - sleepOptimalMin) * 2, 100);
        }
        if (hours >= sleepAcceptableMin && hours < sleepOptimalMin) {
            return (int) (70 + (hours - sleepAcceptableMin) * 20);
        }
        if (hours > sleepOptimalMax) {
            return 80;
        }
        return (int) Math.max(50, hours * 10);
    }

    /**
     * 用药依从评分
     *
     * 规则：已服药次数 / 应服药次数
     */
    private int calcMedicineScore(String userId, LocalDate from, LocalDate to) {
        List<MedicineRecord> taken = medicineRecordMapper.selectByUserAndRange(userId, from, to);
        long takenCount = taken == null ? 0 : taken.stream()
                .filter(r -> "taken".equals(r.getStatus()))
                .count();
        long missedCount = taken == null ? 0 : taken.stream()
                .filter(r -> "missed".equals(r.getStatus()))
                .count();

        long total = takenCount + missedCount;
        if (total == 0) {
            return 100;   // 没有用药计划，视为满分（不扣分）
        }

        return (int) Math.round(takenCount * 100.0 / total);
    }

    /**
     * 运动评分
     *
     * 规则：按日均步数评分
     *   >= 10000 步 → 100 分
     *   >= 6000 步  → 80 分
     *   >= 3000 步  → 60 分
     *   < 3000 步   → 按比例
     */
    private int calcExerciseScore(List<ExerciseRecord> exercises) {
        if (exercises == null || exercises.isEmpty()) {
            return 0;
        }

        int totalSteps = exercises.stream()
                .mapToInt(e -> e.getSteps() == null ? 0 : e.getSteps())
                .sum();

        long days = exercises.stream().map(ExerciseRecord::getDate).distinct().count();
        int avgSteps = days == 0 ? 0 : (int) (totalSteps / days);

        if (avgSteps >= 10000) return 100;
        if (avgSteps >= 6000) return 80;
        if (avgSteps >= 3000) return 60;
        return (int) Math.round(avgSteps / 3000.0 * 60);
    }

    /**
     * 饮食评分
     *
     * 规则：记录完整度 —— 每天三餐都记录为满分
     */
    private int calcDietScore(List<DietRecord> diets) {
        if (diets == null || diets.isEmpty()) {
            return 0;
        }

        // 每天记录了几餐
        Map<LocalDate, Set<String>> byDate = new HashMap<>();
        for (DietRecord d : diets) {
            byDate.computeIfAbsent(d.getDate(), k -> new HashSet<>()).add(d.getMealType());
        }

        double avgMeals = byDate.values().stream().mapToInt(Set::size).average().orElse(0);
        return (int) Math.min(100, Math.round(avgMeals / 3.0 * 100));
    }

    /**
     * 监测评分
     *
     * 规则：从 100 分开始，按告警严重程度扣分
     */
    private int calcMonitorScore(List<Alert> alerts) {
        if (alerts == null || alerts.isEmpty()) {
            return 100;
        }

        int deduct = 0;
        for (Alert a : alerts) {
            deduct += switch (a.getSeverity() == null ? "low" : a.getSeverity()) {
                case "critical" -> 20;
                case "high" -> 10;
                case "medium" -> 5;
                default -> 2;
            };
        }

        return Math.max(0, 100 - deduct);
    }

    // ========================================================================
    // 工具
    // ========================================================================

    private int weighted(int dimensionScore, int weight) {
        return (int) Math.round(dimensionScore * weight / 100.0);
    }

    /** "23:10" -> "06:40" 的小时数（跨天按 +24 计） */
    private double hoursBetween(String sleepTime, String wakeTime) {
        try {
            int sh = Integer.parseInt(sleepTime.split(":")[0]);
            int sm = Integer.parseInt(sleepTime.split(":")[1]);
            int wh = Integer.parseInt(wakeTime.split(":")[0]);
            int wm = Integer.parseInt(wakeTime.split(":")[1]);

            double sleepMin = sh * 60 + sm;
            double wakeMin = wh * 60 + wm;
            if (wakeMin < sleepMin) {
                wakeMin += 24 * 60;   // 跨天
            }
            return (wakeMin - sleepMin) / 60.0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String levelOf(int score) {
        if (score >= 90) return "优秀";
        if (score >= 75) return "良好";
        if (score >= 60) return "一般";
        return "需关注";
    }

    private String suggestionOf(HealthScore s) {
        List<String> tips = new ArrayList<>();

        if (s.getSleepScore() < 70) tips.add("作息不太规律，建议固定睡觉和起床时间");
        if (s.getMedicineScore() < 80) tips.add("有漏服药物，记得按时吃药");
        if (s.getExerciseScore() < 60) tips.add("运动量偏少，建议每天散步 30 分钟");
        if (s.getDietScore() < 60) tips.add("饮食记录不完整，建议每餐都记录一下");
        if (s.getMonitorScore() < 80) tips.add("近期有健康异常提醒，注意休息");

        if (tips.isEmpty()) {
            return "各项指标都很棒，继续保持！🎉";
        }
        return String.join("；", tips) + "。";
    }

    // ========================================================================
    // 结果对象
    // ========================================================================

    @lombok.Data
    public static class HealthScore {
        private String userId;
        private LocalDate date;
        private Integer totalScore;
        private String level;
        private String suggestion;

        // 各维度得分（0-100）
        private Integer sleepScore;
        private Integer medicineScore;
        private Integer exerciseScore;
        private Integer dietScore;
        private Integer monitorScore;
    }
}
