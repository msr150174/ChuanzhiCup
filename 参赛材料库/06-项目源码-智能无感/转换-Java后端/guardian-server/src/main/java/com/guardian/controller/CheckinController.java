package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.entity.CheckinRecord;
import com.guardian.entity.User;
import com.guardian.mapper.CheckinRecordMapper;
import com.guardian.service.AuthService;
import com.guardian.service.HealthScoreService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 作息打卡接口
 *
 * 路由：
 *   POST /api/checkin/check      打卡（睡觉 / 起床）
 *   GET  /api/checkin/today      今日打卡状态
 *   GET  /api/checkin/records    历史记录
 *   GET  /api/checkin/stats      统计（连续天数等）
 *   GET  /api/checkin/health-score  健康评分
 */
@Slf4j
@RestController
@RequestMapping("/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinRecordMapper checkinMapper;
    private final AuthService authService;
    private final HealthScoreService healthScoreService;

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    /**
     * 打卡
     *
     * 请求体：{ "type": "sleep" | "wake" }
     *
     * ★ 用 upsert：同一天多次调用会更新而非插入重复行
     */
    @PostMapping("/check")
    public ApiResponse<Map<String, Object>> check(HttpServletRequest req,
                                                  @RequestBody Map<String, String> body) {
        User user = authService.requireUser(token(req));

        String type = body == null ? null : body.get("type");
        if (!"sleep".equals(type) && !"wake".equals(type)) {
            throw BizException.badRequest("type 必须是 sleep 或 wake");
        }

        LocalDate today = LocalDate.now();
        String now = LocalTime.now().format(HHMM);

        CheckinRecord record = checkinMapper.selectByUserAndDate(user.getId(), today);
        if (record == null) {
            record = new CheckinRecord();
            record.setUserId(user.getId());
            record.setDate(today);
        }

        if ("sleep".equals(type)) {
            record.setSleepTime(now);
        } else {
            record.setWakeTime(now);
            // 起床时算睡眠评分
            if (record.getSleepTime() != null) {
                double hours = hoursBetween(record.getSleepTime(), now);
                record.setSleepScore(healthScoreService.sleepDurationScore(hours));
            }
        }

        checkinMapper.upsert(record);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("time", now);
        data.put("date", today.toString());
        data.put("record", toRecordView(record));

        String label = "sleep".equals(type) ? "睡觉打卡成功" : "起床打卡成功";
        return ApiResponse.ok(label, data);
    }

    /** 今日打卡状态 */
    @GetMapping("/today")
    public ApiResponse<Map<String, Object>> today(HttpServletRequest req) {
        User user = authService.requireUser(token(req));
        LocalDate today = LocalDate.now();

        CheckinRecord r = checkinMapper.selectByUserAndDate(user.getId(), today);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", today.toString());

        if (r == null) {
            data.put("sleepTime", null);
            data.put("wakeTime", null);
            data.put("status", "none");        // 未打卡
            data.put("duration", null);
            data.put("sleepScore", null);
        } else {
            data.put("sleepTime", r.getSleepTime());
            data.put("wakeTime", r.getWakeTime());
            data.put("sleepScore", r.getSleepScore());

            if (r.getSleepTime() != null && r.getWakeTime() == null) {
                data.put("status", "sleeping");    // 已睡未起
                data.put("duration", null);
            } else if (r.getWakeTime() != null) {
                data.put("status", "done");        // 已完成
                double h = hoursBetween(r.getSleepTime(), r.getWakeTime());
                data.put("duration", Math.round(h * 100) / 100.0);
            } else {
                data.put("status", "none");
                data.put("duration", null);
            }
        }

        return ApiResponse.ok(data);
    }

    /** 历史记录（默认最近 30 天） */
    @GetMapping("/records")
    public ApiResponse<Map<String, Object>> records(HttpServletRequest req,
                                                    @RequestParam(defaultValue = "30") int days) {
        User user = authService.requireUser(token(req));

        int d = Math.max(1, Math.min(days, 365));
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(d - 1L);

        List<CheckinRecord> list = checkinMapper.selectByUserAndRange(user.getId(), from, to);

        List<Map<String, Object>> views = new ArrayList<>();
        for (CheckinRecord r : list) {
            views.add(toRecordView(r));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", views);
        data.put("total", views.size());
        data.put("from", from.toString());
        data.put("to", to.toString());
        return ApiResponse.ok(data);
    }

    /** 打卡统计 */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(HttpServletRequest req) {
        User user = authService.requireUser(token(req));
        LocalDate today = LocalDate.now();

        // 最近 60 天的打卡日期，用于算连续天数
        List<LocalDate> dates = checkinMapper.selectRecentDates(user.getId());

        int continuous = calcContinuous(dates, today);
        int weekDays = checkinMapper.countDays(user.getId(), today.minusDays(6), today);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("continuousDays", continuous);
        data.put("weekDays", weekDays);
        data.put("totalDays", dates.size());
        data.put("weekTarget", 7);
        return ApiResponse.ok(data);
    }

    /** 健康评分（五项加权） */
    @GetMapping("/health-score")
    public ApiResponse<HealthScoreService.HealthScore> healthScore(HttpServletRequest req) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(healthScoreService.calculate(user.getId()));
    }

    // ========================================================================
    // 工具
    // ========================================================================

    /**
     * 连续打卡天数
     *
     * 从今天往前推：如果今天没打卡，从昨天开始算；
     * 一旦遇到断档就停止计数。
     */
    private int calcContinuous(List<LocalDate> dates, LocalDate today) {
        if (dates == null || dates.isEmpty()) return 0;

        Set<LocalDate> set = new HashSet<>(dates);

        // 今天还没打卡时，允许从昨天开始
        LocalDate cursor = set.contains(today) ? today : today.minusDays(1);
        if (!set.contains(cursor)) return 0;

        int count = 0;
        while (set.contains(cursor)) {
            count++;
            cursor = cursor.minusDays(1);
        }
        return count;
    }

    /** "23:10" -> "06:40" 的小时数（跨天按 +24 计） */
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

    /** 转成前端视图，顺便算出时长 */
    private Map<String, Object> toRecordView(CheckinRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("date", r.getDate() == null ? null : r.getDate().toString());
        m.put("sleepTime", r.getSleepTime());
        m.put("wakeTime", r.getWakeTime());
        m.put("sleepScore", r.getSleepScore());
        m.put("wakeScore", r.getWakeScore());

        if (r.getSleepTime() != null && r.getWakeTime() != null) {
            double h = hoursBetween(r.getSleepTime(), r.getWakeTime());
            m.put("duration", Math.round(h * 100) / 100.0);
            m.put("status", "done");
        } else if (r.getSleepTime() != null) {
            m.put("duration", null);
            m.put("status", "sleeping");
        } else {
            m.put("duration", null);
            m.put("status", "none");
        }

        return m;
    }
}
