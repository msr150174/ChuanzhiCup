package com.guardian.controller;

import com.guardian.ai.AiService;
import com.guardian.ai.LocalRuleProvider.AiContext;
import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.entity.User;
import com.guardian.service.AuthService;
import com.guardian.service.HealthScoreService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI 接口
 *
 * 路由：
 *   POST /api/ai/chat            对话
 *   POST /api/ai/sleep-analysis  睡眠分析
 *   GET  /api/ai/report/daily    每日报告
 *   GET  /api/ai/report/weekly   每周报告
 *
 * ★ 所有调用都经后端中转，前端拿不到任何 AI 密钥
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final AuthService authService;
    private final HealthScoreService healthScoreService;

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    /**
     * AI 对话
     *
     * 请求体：
     *   { "message": "...", "conversationId": "...", "context": { ... } }
     *
     * 响应 data：
     *   { "content": "...", "conversationId": "...", "provider": "ollama|coze|rule", "responseTime": 123 }
     */
    @PostMapping("/chat")
    public ApiResponse<Map<String, Object>> chat(HttpServletRequest req,
                                                 @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));

        String message = body == null ? null : (String) body.get("message");
        if (message == null || message.isBlank()) {
            throw BizException.badRequest("消息不能为空");
        }
        if (message.length() > 500) {
            throw BizException.badRequest("消息过长（最多 500 字）");
        }

        String conversationId = body.get("conversationId") == null
                ? null : String.valueOf(body.get("conversationId"));

        // 组装用户健康上下文，让 AI 回复更个性化
        AiContext ctx = buildContext(user.getId(), body);

        AiService.ChatResult result = aiService.chat(message.trim(), conversationId, ctx);

        Map<String, Object> data = new HashMap<>();
        data.put("content", result.getContent());
        data.put("conversationId", result.getConversationId());
        data.put("provider", result.getProvider());
        data.put("responseTime", result.getResponseTime());
        return ApiResponse.ok(data);
    }

    /**
     * 从请求体或实时数据组装 AI 上下文
     *
     * 优先用前端传来的（那是页面已加载的数据），没有则实时算。
     */
    private AiContext buildContext(String userId, Map<String, Object> body) {
        AiContext ctx = new AiContext();

        Object raw = body == null ? null : body.get("context");
        if (raw instanceof Map<?, ?> m) {
            ctx.setHealthScore(toInt(m.get("healthScore")));
            ctx.setTodaySteps(toInt(m.get("todaySteps")));
            ctx.setSleepHours(toDouble(m.get("sleepHours")));
            ctx.setMedicineTaken(toBool(m.get("medicineTaken")));
            ctx.setLocation(m.get("location") == null ? null : String.valueOf(m.get("location")));
        }

        // 前端没传健康评分时，实时计算一次
        if (ctx.getHealthScore() == null) {
            try {
                ctx.setHealthScore(healthScoreService.calculate(userId).getTotalScore());
            } catch (Exception e) {
                // 计算失败不影响对话
            }
        }

        return ctx;
    }

    /**
     * 睡眠分析
     *
     * 请求体：{ "sleepHours": 7.5, "wakeTime": "06:30", "sleepTime": "23:00" }
     */
    @PostMapping("/sleep-analysis")
    public ApiResponse<Map<String, Object>> sleepAnalysis(HttpServletRequest req,
                                                          @RequestBody Map<String, Object> body) {
        authService.requireUser(token(req));

        Double hours = toDouble(body == null ? null : body.get("sleepHours"));
        if (hours == null) {
            throw BizException.badRequest("缺少 sleepHours");
        }

        int score = healthScoreService.sleepDurationScore(hours);

        Map<String, Object> data = new HashMap<>();
        data.put("sleepHours", hours);
        data.put("score", score);
        data.put("level", score >= 90 ? "优秀" : score >= 75 ? "良好" : score >= 60 ? "一般" : "需改善");
        data.put("suggestion", sleepSuggestion(hours));
        return ApiResponse.ok(data);
    }

    private String sleepSuggestion(double h) {
        if (h >= 7 && h <= 9) return "睡眠时长很理想，继续保持规律作息 🌙";
        if (h > 9) return "睡眠时间偏长，建议适当缩短，白天多活动 ☀️";
        if (h >= 6) return "睡眠略有不足，建议提前半小时上床 😴";
        return "睡眠明显不足，建议调整作息，保证 7-8 小时 💤";
    }

    /** 每日报告 */
    @GetMapping("/report/daily")
    public ApiResponse<Map<String, Object>> dailyReport(HttpServletRequest req) {
        User user = authService.requireUser(token(req));
        HealthScoreService.HealthScore score = healthScoreService.calculate(user.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("date", score.getDate().toString());
        data.put("totalScore", score.getTotalScore());
        data.put("level", score.getLevel());
        data.put("suggestion", score.getSuggestion());
        data.put("dimensions", Map.of(
                "sleep", score.getSleepScore(),
                "medicine", score.getMedicineScore(),
                "exercise", score.getExerciseScore(),
                "diet", score.getDietScore(),
                "monitor", score.getMonitorScore()
        ));
        return ApiResponse.ok(data);
    }

    /** 每周报告（结构与日报一致，前端自行做趋势对比） */
    @GetMapping("/report/weekly")
    public ApiResponse<Map<String, Object>> weeklyReport(HttpServletRequest req) {
        User user = authService.requireUser(token(req));
        HealthScoreService.HealthScore score = healthScoreService.calculate(user.getId());

        Map<String, Object> data = new HashMap<>();
        data.put("range", score.getDate().minusDays(6) + " ~ " + score.getDate());
        data.put("totalScore", score.getTotalScore());
        data.put("level", score.getLevel());
        data.put("suggestion", score.getSuggestion());
        data.put("dimensions", Map.of(
                "sleep", score.getSleepScore(),
                "medicine", score.getMedicineScore(),
                "exercise", score.getExerciseScore(),
                "diet", score.getDietScore(),
                "monitor", score.getMonitorScore()
        ));
        return ApiResponse.ok(data);
    }

    // ---------------- 类型转换（请求体是松散 Map，需要防御性转换） ----------------

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

    private Boolean toBool(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(o));
    }
}
