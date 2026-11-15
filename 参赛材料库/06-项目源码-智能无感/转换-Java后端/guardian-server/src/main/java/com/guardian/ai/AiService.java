package com.guardian.ai;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.guardian.ai.LocalRuleProvider.AiContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AI 编排服务 —— 三层降级
 *
 * 路由顺序（任何一层失败都自动降级，绝不向用户报错）：
 *   ① Ollama（本地模型，最快最省钱）
 *   ② Coze（云端模型，能力强）
 *   ③ 规则库（本地兜底，永远可用）
 *
 * 另加：响应缓存（Redis），相同问题 5 分钟内直接返回
 */
@Slf4j
@Service
public class AiService {

    private final LocalRuleProvider ruleProvider;
    private final StringRedisTemplate redis;

    // ---------------- 配置 ----------------
    @Value("${guardian.ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${guardian.ai.ollama.enabled:false}")
    private boolean ollamaEnabled;
    @Value("${guardian.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;
    @Value("${guardian.ai.ollama.model:qwen2.5:7b}")
    private String ollamaModel;
    @Value("${guardian.ai.ollama.timeout-seconds:30}")
    private int ollamaTimeout;

    @Value("${guardian.ai.coze.enabled:false}")
    private boolean cozeEnabled;
    @Value("${guardian.ai.coze.base-url:https://api.coze.cn}")
    private String cozeBaseUrl;
    @Value("${guardian.ai.coze.api-token:}")
    private String cozeToken;
    @Value("${guardian.ai.coze.bot-id:}")
    private String cozeBotId;
    @Value("${guardian.ai.coze.timeout-seconds:8}")
    private int cozeTimeout;

    @Value("${guardian.ai.cache.enabled:true}")
    private boolean cacheEnabled;
    @Value("${guardian.ai.cache.ttl-seconds:300}")
    private long cacheTtl;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public AiService(LocalRuleProvider ruleProvider, StringRedisTemplate redis) {
        this.ruleProvider = ruleProvider;
        this.redis = redis;
    }

    // ========================================================================
    // 对外主入口
    // ========================================================================

    /**
     * AI 对话
     *
     * @param message        用户消息
     * @param conversationId 会话ID（可空）
     * @param ctx            用户健康上下文（可空）
     */
    public ChatResult chat(String message, String conversationId, AiContext ctx) {
        long start = System.currentTimeMillis();

        if (StrUtil.isBlank(message)) {
            return ChatResult.of("你想说点什么呢？我在这儿听着呢~ 😊", conversationId, "rule", start);
        }

        String text = message.trim();

        // ---------- 缓存 ----------
        String cacheKey = "guardian:ai:cache:" + Integer.toHexString(text.toLowerCase().hashCode());
        if (cacheEnabled) {
            try {
                String hit = redis.opsForValue().get(cacheKey);
                if (StrUtil.isNotBlank(hit)) {
                    log.debug("[AI] 缓存命中: {}", text.substring(0, Math.min(20, text.length())));
                    return ChatResult.of(hit, conversationId, "cache", start);
                }
            } catch (Exception e) {
                log.debug("[AI] 缓存读取失败（忽略）: {}", e.getMessage());
            }
        }

        if (!aiEnabled) {
            String reply = ruleProvider.reply(text, ctx == null ? AiContext.empty() : ctx);
            return ChatResult.of(reply, conversationId, "rule", start);
        }

        // ---------- ① Ollama ----------
        if (ollamaEnabled) {
            try {
                String reply = callOllama(text, ctx);
                if (StrUtil.isNotBlank(reply)) {
                    cache(cacheKey, reply);
                    log.info("[AI] Ollama 成功，耗时 {}ms", System.currentTimeMillis() - start);
                    return ChatResult.of(reply, conversationId, "ollama", start);
                }
            } catch (Exception e) {
                log.warn("[AI] Ollama 失败，降级到 Coze: {}", e.getMessage());
            }
        }

        // ---------- ② Coze ----------
        if (cozeEnabled && StrUtil.isNotBlank(cozeToken) && StrUtil.isNotBlank(cozeBotId)) {
            try {
                String reply = callCoze(text, conversationId, ctx);
                if (StrUtil.isNotBlank(reply)) {
                    cache(cacheKey, reply);
                    log.info("[AI] Coze 成功，耗时 {}ms", System.currentTimeMillis() - start);
                    return ChatResult.of(reply, conversationId, "coze", start);
                }
            } catch (Exception e) {
                log.warn("[AI] Coze 失败，降级到规则库: {}", e.getMessage());
            }
        }

        // ---------- ③ 规则库兜底 ----------
        String reply = ruleProvider.reply(text, ctx == null ? AiContext.empty() : ctx);
        cache(cacheKey, reply);
        log.info("[AI] 规则库兜底，耗时 {}ms", System.currentTimeMillis() - start);
        return ChatResult.of(reply, conversationId, "rule", start);
    }

    // ========================================================================
    // Ollama
    // ========================================================================

    private String callOllama(String message, AiContext ctx) throws Exception {
        String prompt = buildPrompt(message, ctx);

        JSONObject body = new JSONObject();
        body.set("model", ollamaModel);
        body.set("stream", false);
        body.set("messages", new Object[]{
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", prompt)
        });

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ollamaBaseUrl + "/api/chat"))
                .timeout(Duration.ofSeconds(ollamaTimeout))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) {
            throw new IllegalStateException("Ollama HTTP " + res.statusCode());
        }

        JSONObject json = JSONUtil.parseObj(res.body());
        JSONObject msgObj = json.getJSONObject("message");
        return msgObj == null ? null : msgObj.getStr("content");
    }

    // ========================================================================
    // Coze
    // ========================================================================

    /**
     * 调用 Coze 智能体（流式）。
     *
     * ★ 为什么必须用流式：
     *   Coze 的非流式接口要三步走 —— chat 发起 → retrieve 轮询状态 →
     *   message/list 取回复。实测 message/list 这个接口对当前 PAT 返回
     *   code 4100 "authentication is invalid"（chat 和 retrieve 都正常），
     *   因此非流式路径永远拿不到回复。
     *
     *   流式接口把回复直接放在 SSE 事件流里（conversation.message.delta），
     *   不需要调用 message/list，所以不受该限制影响，而且更快
     *   （实测 4.7 秒 vs 轮询 5.8 秒以上）。
     */
    private String callCoze(String message, String conversationId, AiContext ctx) throws Exception {
        JSONObject body = new JSONObject();
        body.set("bot_id", cozeBotId);
        body.set("user_id", "guardian-user");
        body.set("stream", true);
        body.set("auto_save_history", true);
        body.set("additional_messages", new Object[]{
                Map.of("role", "user", "content", buildPrompt(message, ctx), "content_type", "text")
        });
        if (StrUtil.isNotBlank(conversationId)) {
            body.set("conversation_id", conversationId);
        }

        HttpRequest chatReq = HttpRequest.newBuilder()
                .uri(URI.create(cozeBaseUrl + "/v3/chat"))
                .timeout(Duration.ofSeconds(cozeTimeout))
                .header("Authorization", "Bearer " + cozeToken)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> chatRes =
                http.send(chatReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (chatRes.statusCode() != 200) {
            throw new IllegalStateException("Coze HTTP " + chatRes.statusCode());
        }

        String raw = chatRes.body();
        if (StrUtil.isBlank(raw)) {
            throw new IllegalStateException("Coze 返回空响应");
        }

        // SSE 里如果有错误码，提前抛出来让上层降级
        if (raw.contains("\"code\":4100") || raw.contains("\"code\": 4100")) {
            throw new IllegalStateException("Coze 认证失败(4100)");
        }

        StringBuilder answer = new StringBuilder();

        // Coze 的 SSE 流里同一个回复会出现两次：
        //   event:conversation.message.delta      data.type=answer  逐字增量 ← 累加这个
        //   event:conversation.message.completed  data.type=answer  完整全文 ← 必须跳过
        // data 的 JSON 里没有事件名，事件名单独占一行 "event:xxx"，
        // 所以要在循环里记住当前事件名，据此区分增量和全文。
        String currentEvent = "";

        for (String line : raw.split("\n")) {
            String s = line.trim();

            if (s.startsWith("event:")) {
                currentEvent = s.substring(6).trim();
                if (currentEvent.contains("failed")) {
                    throw new IllegalStateException("Coze 事件异常: " + currentEvent);
                }
                continue;
            }

            if (!s.startsWith("data:")) {
                continue;
            }

            // 只处理增量事件，其余（completed / verbose / follow_up）一律忽略
            if (!currentEvent.contains("delta")) {
                continue;
            }

            String payload = s.substring(5).trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                continue;
            }

            try {
                JSONObject obj = JSONUtil.parseObj(payload);

                if (!"answer".equals(obj.getStr("type"))) {
                    continue;
                }

                String content = obj.getStr("content");
                if (StrUtil.isNotBlank(content)) {
                    answer.append(content);
                }
            } catch (Exception parseEx) {
                // 单行解析失败不影响整体，继续读后续增量
                log.debug("[AI] 跳过无法解析的 SSE 行: {}", StrUtil.maxLength(payload, 80));
            }
        }

        String result = answer.toString().trim();
        if (result.isEmpty()) {
            throw new IllegalStateException("Coze 未返回内容");
        }

        return result;
    }

    // ========================================================================
    // 辅助
    // ========================================================================

    private static final String SYSTEM_PROMPT = """
            你是「小护」，一位温暖、耐心的老年人健康守护助手。
            你的职责：解答健康问题、提醒用药、分析作息、陪伴聊天。
            说话要求：
            1. 称呼用户为「你」，语气亲切，像家人一样
            2. 回复简短（不超过 150 字），避免专业术语
            3. 适当使用 emoji 让文字更温暖
            4. 遇到紧急情况（摔倒、胸痛、呼吸困难），立即提示拨打 120 或点击 SOS
            5. 不做医疗诊断，只给生活建议
            """;

    /** 把用户健康数据拼进提问，让回复更个性化 */
    private String buildPrompt(String message, AiContext ctx) {
        if (ctx == null) return message;

        List<String> parts = new ArrayList<>();
        if (ctx.getHealthScore() != null) parts.add("今日健康评分 " + ctx.getHealthScore() + " 分");
        if (ctx.getTodaySteps() != null) parts.add("今日步数 " + ctx.getTodaySteps() + " 步");
        if (ctx.getSleepHours() != null) parts.add(String.format("昨晚睡眠 %.1f 小时", ctx.getSleepHours()));
        if (ctx.getMedicineTaken() != null) parts.add("今日服药 " + (ctx.getMedicineTaken() ? "已完成" : "未完成"));
        if (StrUtil.isNotBlank(ctx.getLocation())) parts.add("当前位置 " + ctx.getLocation());

        if (parts.isEmpty()) return message;

        return "【用户健康数据】" + String.join("，", parts) + "\n\n【用户问题】" + message;
    }

    private void cache(String key, String value) {
        if (!cacheEnabled || StrUtil.isBlank(value)) return;
        try {
            // 只缓存较短的回复，避免占满 Redis
            if (value.length() <= 1000) {
                redis.opsForValue().set(key, value, cacheTtl, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.debug("[AI] 缓存写入失败（忽略）: {}", e.getMessage());
        }
    }

    // ========================================================================
    // 结果封装
    // ========================================================================

    @Data
    public static class ChatResult {
        private String content;
        private String conversationId;
        /** 实际提供方：ollama / coze / rule / cache */
        private String provider;
        private Long responseTime;

        static ChatResult of(String content, String convId, String provider, long start) {
            ChatResult r = new ChatResult();
            r.content = content;
            r.conversationId = StrUtil.isBlank(convId) ? "guardian-" + System.currentTimeMillis() : convId;
            r.provider = provider;
            r.responseTime = System.currentTimeMillis() - start;
            return r;
        }
    }
}
