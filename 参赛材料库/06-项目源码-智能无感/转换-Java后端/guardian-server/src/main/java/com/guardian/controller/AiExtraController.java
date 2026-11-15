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
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AI 补充接口
 *
 * 覆盖原 NestJS 项目 ai 模块的其余路由：
 *   POST /api/ai/analyze         数据健康分析
 *   POST /api/ai/chat/enhanced   增强对话（带健康上下文）
 *   POST /api/ai/tts             文本转语音（占位）
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiExtraController {

    private final AiService aiService;
    private final AuthService authService;
    private final HealthScoreService healthScoreService;

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    /**
     * 数据健康分析
     *
     * 请求体：{ "data": { "sleepHours": 7.5, "steps": 8000, ... } }
     * 把数据描述成自然语言交给 AI，返回分析结论。
     */
    @PostMapping("/analyze")
    public ApiResponse<Map<String, Object>> analyze(HttpServletRequest req,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        User user = authService.requireUser(token(req));

        // 组装分析提示
        StringBuilder sb = new StringBuilder("请根据以下健康数据给出简短分析和建议：\n");

        Object dataObj = body == null ? null : body.get("data");
        if (dataObj instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
        }

        // 数据不足时用实时健康评分
        HealthScoreService.HealthScore score = null;
        try {
            score = healthScoreService.calculate(user.getId());
        } catch (Exception e) {
            log.debug("健康评分计算失败: {}", e.getMessage());
        }

        if (dataObj == null && score != null) {
            sb.append("- 健康总分: ").append(score.getTotalScore()).append("\n");
            sb.append("- 作息: ").append(score.getSleepScore()).append("\n");
            sb.append("- 用药: ").append(score.getMedicineScore()).append("\n");
            sb.append("- 运动: ").append(score.getExerciseScore()).append("\n");
            sb.append("- 饮食: ").append(score.getDietScore()).append("\n");
            sb.append("- 监测: ").append(score.getMonitorScore()).append("\n");
        }

        AiContext ctx = new AiContext();
        if (score != null) {
            ctx.setHealthScore(score.getTotalScore());
        }

        AiService.ChatResult result = aiService.chat(sb.toString(), null, ctx);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("analysis", result.getContent());
        data.put("provider", result.getProvider());
        data.put("responseTime", result.getResponseTime());
        if (score != null) {
            data.put("healthScore", score.getTotalScore());
            data.put("dimensions", Map.of(
                    "sleep", score.getSleepScore(),
                    "medicine", score.getMedicineScore(),
                    "exercise", score.getExerciseScore(),
                    "diet", score.getDietScore(),
                    "monitor", score.getMonitorScore()
            ));
        }
        return ApiResponse.ok(data);
    }

    /**
     * 增强对话
     *
     * 与 /chat 的区别：**强制注入**完整健康上下文，
     * 让回复更贴合用户当前状态。
     *
     * 请求体：{ "message": "...", "conversationId": "...", "includeHealth": true }
     */
    @PostMapping("/chat/enhanced")
    public ApiResponse<Map<String, Object>> enhancedChat(HttpServletRequest req,
                                                         @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));

        String message = body == null ? null : str(body.get("message"));
        if (message == null || message.isBlank()) {
            throw BizException.badRequest("消息不能为空");
        }

        String conversationId = body.get("conversationId") == null
                ? null : str(body.get("conversationId"));

        // 强制拉取健康评分
        AiContext ctx = new AiContext();
        try {
            HealthScoreService.HealthScore score = healthScoreService.calculate(user.getId());
            ctx.setHealthScore(score.getTotalScore());
        } catch (Exception e) {
            log.debug("健康评分计算失败: {}", e.getMessage());
        }

        Object raw = body.get("context");
        if (raw instanceof Map<?, ?> m) {
            if (m.get("todaySteps") != null) {
                try {
                    ctx.setTodaySteps((int) Double.parseDouble(str(m.get("todaySteps"))));
                } catch (Exception ignored) {
                    // 类型不对就跳过
                }
            }
            if (m.get("sleepHours") != null) {
                try {
                    ctx.setSleepHours(Double.parseDouble(str(m.get("sleepHours"))));
                } catch (Exception ignored) {
                    // 同上
                }
            }
            if (m.get("location") != null) {
                ctx.setLocation(str(m.get("location")));
            }
        }

        AiService.ChatResult result = aiService.chat(message.trim(), conversationId, ctx);

        // 推荐追问
        List<String> followUp = List.of(
                "帮我分析一下最近的睡眠",
                "今天适合做什么运动",
                "我的用药提醒设置好了吗"
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", result.getContent());
        data.put("conversationId", result.getConversationId());
        data.put("provider", result.getProvider());
        data.put("responseTime", result.getResponseTime());
        data.put("followUp", followUp);
        data.put("hasEmoji", true);
        data.put("healthContext", Map.of(
                "healthScore", ctx.getHealthScore() == null ? 0 : ctx.getHealthScore(),
                "todaySteps", ctx.getTodaySteps() == null ? 0 : ctx.getTodaySteps()
        ));
        return ApiResponse.ok(data);
    }

    /**
     * 文本转语音
     *
     * ★ 未接入真实 TTS 服务（需要云端语音合成 API Key）。
     *   这里明确返回 available=false 并给出前端可用的降级方案，
     *   而不是假装成功——前端可改用微信的 wx.createInnerAudioContext 或系统朗读。
     */
    @PostMapping("/tts")
    public ApiResponse<Map<String, Object>> tts(HttpServletRequest req,
                                                @RequestBody(required = false) Map<String, Object> body) {
        authService.requireUser(token(req));

        String text = body == null ? null : str(body.get("text"));
        if (text == null || text.isBlank()) {
            throw BizException.badRequest("文本不能为空");
        }
        if (text.length() > 500) {
            throw BizException.badRequest("文本过长（最多 500 字）");
        }

        log.info("TTS 请求（未接入云端服务）: {} 字", text.length());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", false);
        data.put("reason", "未配置云端语音合成服务");
        data.put("text", text);
        data.put("textLength", text.length());
        data.put("fallback", "前端可使用系统内置朗读能力");
        return ApiResponse.ok("语音合成暂未开放", data);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
