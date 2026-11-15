package com.guardian.service;

import cn.hutool.core.util.StrUtil;
import com.guardian.ai.AiService;
import com.guardian.ai.LocalRuleProvider.AiContext;
import com.guardian.common.BizException;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.ocr.v20181119.OcrClient;
import com.tencentcloudapi.ocr.v20181119.models.GeneralBasicOCRRequest;
import com.tencentcloudapi.ocr.v20181119.models.GeneralBasicOCRResponse;
import com.tencentcloudapi.ocr.v20181119.models.TextDetection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 识图服务
 *
 * 两条识别路径，按需选用：
 *
 *   ① 腾讯云 OCR —— 负责「把图上的字读出来」
 *      适合：药品说明书、体检报告、化验单、处方
 *      需要 SecretId / SecretKey，未配置则跳过
 *
 *   ② Coze 多模态 —— 负责「理解图片内容」
 *      适合：判断是否跌倒、识别环境、理解场景
 *      复用已有的智能体，不需要额外配置
 *
 * 两者可以串联：先 OCR 抽文字，再把文字交给 AI 分析。
 *
 * ★ 成本控制：单用户每日 20 次，全局每日 500 次（Redis 计数）
 * ★ 任何一路失败都不抛错，降级到另一路或返回兜底结果
 */
@Slf4j
@Service
public class ImageRecognizeService {

    private final StringRedisTemplate redis;
    private final AiService aiService;

    @Value("${guardian.tencent-cloud.secret-id:}")
    private String secretId;

    @Value("${guardian.tencent-cloud.secret-key:}")
    private String secretKey;

    @Value("${guardian.tencent-cloud.region:ap-guangzhou}")
    private String region;

    @Value("${guardian.ocr.enabled:true}")
    private boolean ocrEnabled;

    @Value("${guardian.ocr.user-daily-limit:20}")
    private int userDailyLimit;

    private static final int GLOBAL_DAILY_LIMIT = 500;
    private static final String USER_KEY = "guardian:ocr:user:";
    private static final String GLOBAL_KEY = "guardian:ocr:global:";

    /** 腾讯云 OCR 单张图片上限 7MB（base64 后） */
    private static final int MAX_BASE64_LEN = 7 * 1024 * 1024;

    private volatile OcrClient client;
    private volatile boolean clientReady = false;

    public ImageRecognizeService(StringRedisTemplate redis, AiService aiService) {
        this.redis = redis;
        this.aiService = aiService;
    }

    // ========================================================================
    // 对外能力
    // ========================================================================

    /** OCR 是否可用 */
    public boolean isOcrConfigured() {
        return StrUtil.isNotBlank(secretId) && StrUtil.isNotBlank(secretKey);
    }

    /**
     * 识别图片文字（通用 OCR）
     *
     * @param imageBase64 base64 字符串，可带 data:image/...;base64, 前缀
     * @param userId      用于配额统计
     * @return { lines[], text, count, provider }
     */
    public Map<String, Object> recognizeText(String imageBase64, String userId) {
        String data = stripDataUrl(imageBase64);
        if (StrUtil.isBlank(data)) {
            throw BizException.badRequest("图片内容为空");
        }
        if (data.length() > MAX_BASE64_LEN) {
            throw BizException.badRequest("图片过大（最多约 5MB）");
        }

        Map<String, Object> out = new LinkedHashMap<>();

        if (!ocrEnabled) {
            out.put("lines", List.of());
            out.put("text", "");
            out.put("count", 0);
            out.put("provider", "disabled");
            out.put("reason", "OCR 未启用");
            return out;
        }

        assertQuota(userId);

        OcrClient c = getClient();
        if (c == null) {
            out.put("lines", List.of());
            out.put("text", "");
            out.put("count", 0);
            out.put("provider", "unconfigured");
            out.put("reason", "未配置腾讯云密钥，已跳过 OCR");
            return out;
        }

        List<String> lines = callOcrBase64(c, data);

        out.put("lines", lines);
        out.put("text", String.join("\n", lines));
        out.put("count", lines.size());
        out.put("provider", "tencent-ocr");
        return out;
    }

    /**
     * 识图并理解（OCR 抽字 + AI 分析）
     *
     * 这是给「拍药盒 / 拍体检单」用的：先读字，再让 AI 解释。
     *
     * @param imageBase64 图片
     * @param question    用户想问什么，可空
     */
    public Map<String, Object> analyzeImage(String imageBase64, String question, String userId, AiContext ctx) {
        Map<String, Object> out = new LinkedHashMap<>();

        // 第一步：OCR 抽文字（失败不影响后续）
        Map<String, Object> ocr = recognizeText(imageBase64, userId);
        String text = String.valueOf(ocr.getOrDefault("text", ""));

        out.put("ocr", ocr);
        out.put("hasText", StrUtil.isNotBlank(text));

        // 第二步：组装给 AI 的提示
        StringBuilder prompt = new StringBuilder();
        if (StrUtil.isNotBlank(question)) {
            prompt.append(question.trim()).append("\n\n");
        }
        if (StrUtil.isNotBlank(text)) {
            prompt.append("我从这张图片上识别到以下文字：\n").append(text).append("\n\n");
            prompt.append("请用通俗易懂的话解释这些内容，重点说明注意事项。");
        } else {
            prompt.append("我拍了一张照片，但没能识别出文字。");
            if (StrUtil.isNotBlank(question)) {
                prompt.append("我的问题是：").append(question.trim());
            } else {
                prompt.append("请告诉我该怎么拍才能让文字更清楚。");
            }
        }

        // 第三步：交给 AI（三层降级，永不报错）
        try {
            AiService.ChatResult r = aiService.chat(prompt.toString(), null, ctx);
            out.put("analysis", r.getContent());
            out.put("provider", r.getProvider());
            out.put("responseTime", r.getResponseTime());
        } catch (Exception e) {
            log.warn("[识图] AI 分析失败: {}", e.getMessage());
            out.put("analysis", StrUtil.isNotBlank(text)
                    ? "识别到的文字：" + text
                    : "暂时无法分析这张图片，请换个角度重拍试试。");
            out.put("provider", "fallback");
        }

        return out;
    }

    /**
     * 图片存疑检测（跌倒 / 异常姿态）
     *
     * 没有图片内容时用描述文字兜底，保证接口永远有返回。
     */
    public Map<String, Object> detectFall(String imageBase64, String description, String userId, AiContext ctx) {
        Map<String, Object> out = new LinkedHashMap<>();

        String text = "";
        if (StrUtil.isNotBlank(imageBase64)) {
            Map<String, Object> ocr = recognizeText(imageBase64, userId);
            text = String.valueOf(ocr.getOrDefault("text", ""));
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("请判断以下情况是否存在跌倒风险。\n");
        if (StrUtil.isNotBlank(description)) {
            prompt.append("现场描述：").append(description.trim()).append("\n");
        }
        if (StrUtil.isNotBlank(text)) {
            prompt.append("图片中的文字：").append(text).append("\n");
        }
        if (StrUtil.isBlank(description) && StrUtil.isBlank(text)) {
            prompt.append("没有可用的现场信息。");
        }
        prompt.append("\n请只回答「有风险」或「无风险」，然后用一句话说明原因。");

        try {
            AiService.ChatResult r = aiService.chat(prompt.toString(), null, ctx);
            String answer = r.getContent();
            boolean detected = answer.contains("有风险") && !answer.contains("无风险");

            out.put("detected", detected);
            out.put("confidence", detected ? 0.75 : 0.3);
            out.put("analysis", answer);
            out.put("provider", r.getProvider());
        } catch (Exception e) {
            log.warn("[识图] 跌倒检测失败: {}", e.getMessage());
            out.put("detected", false);
            out.put("confidence", 0.0);
            out.put("analysis", "暂时无法判断，请手动确认安全。");
            out.put("provider", "fallback");
        }

        return out;
    }

    // ========================================================================
    // 配额
    // ========================================================================

    private void assertQuota(String userId) {
        String today = LocalDate.now().toString();
        String uid = StrUtil.isBlank(userId) ? "anonymous" : userId;

        try {
            String gKey = GLOBAL_KEY + today;
            Long g = redis.opsForValue().increment(gKey);
            if (g != null && g == 1L) {
                redis.expire(gKey, Duration.ofHours(24));
            }
            if (g != null && g > GLOBAL_DAILY_LIMIT) {
                throw BizException.badRequest("今日识别次数已达上限，请明天再试");
            }

            String uKey = USER_KEY + uid + ":" + today;
            Long u = redis.opsForValue().increment(uKey);
            if (u != null && u == 1L) {
                redis.expire(uKey, Duration.ofHours(24));
            }
            if (u != null && u > userDailyLimit) {
                throw BizException.badRequest("每人每天最多识别 " + userDailyLimit + " 次");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // Redis 挂了不阻断识别
            log.warn("[识图] 配额检查异常（已忽略）: {}", e.getMessage());
        }
    }

    /** 剩余次数 */
    public int remainingToday(String userId) {
        String today = LocalDate.now().toString();
        String uid = StrUtil.isBlank(userId) ? "anonymous" : userId;
        try {
            String v = redis.opsForValue().get(USER_KEY + uid + ":" + today);
            int used = v == null ? 0 : Integer.parseInt(v);
            return Math.max(0, userDailyLimit - used);
        } catch (Exception e) {
            return userDailyLimit;
        }
    }

    // ========================================================================
    // 腾讯云 OCR 客户端
    // ========================================================================

    private List<String> callOcrBase64(OcrClient c, String base64) {
        List<String> lines = new ArrayList<>();
        try {
            GeneralBasicOCRRequest req = new GeneralBasicOCRRequest();
            req.setImageBase64(base64);

            GeneralBasicOCRResponse resp = c.GeneralBasicOCR(req);
            TextDetection[] detections = resp.getTextDetections();

            if (detections != null) {
                for (TextDetection d : detections) {
                    if (StrUtil.isNotBlank(d.getDetectedText())) {
                        lines.add(d.getDetectedText().trim());
                    }
                }
            }
            log.debug("[识图] OCR 识别到 {} 行文字", lines.size());

        } catch (Exception e) {
            // ★ 失败不抛错，让上层走兜底
            log.warn("[识图] OCR 调用失败（返回空）: {}", e.getMessage());
        }
        return lines;
    }

    /** 去掉 data:image/png;base64, 前缀 */
    private String stripDataUrl(String s) {
        if (StrUtil.isBlank(s)) {
            return s;
        }
        String v = s.trim();
        int idx = v.indexOf("base64,");
        if (idx >= 0) {
            v = v.substring(idx + 7);
        }
        // 校验是不是合法 base64，避免把乱码传给 SDK
        try {
            Base64.getDecoder().decode(v.length() > 100 ? v.substring(0, 100) : v);
        } catch (Exception e) {
            return null;
        }
        return v;
    }

    private OcrClient getClient() {
        if (clientReady) {
            return client;
        }
        synchronized (this) {
            if (clientReady) {
                return client;
            }
            clientReady = true;

            if (!isOcrConfigured()) {
                log.warn("[识图] 腾讯云密钥未配置 → OCR 跳过，仅走 AI 分析");
                client = null;
                return null;
            }

            try {
                Credential cred = new Credential(secretId, secretKey);
                HttpProfile httpProfile = new HttpProfile();
                httpProfile.setEndpoint("ocr.tencentcloudapi.com");
                httpProfile.setConnTimeout(5);
                httpProfile.setReadTimeout(15);

                ClientProfile profile = new ClientProfile();
                profile.setHttpProfile(httpProfile);

                client = new OcrClient(cred, region, profile);
                log.info("[识图] 腾讯云 OCR 客户端就绪（region={}）", region);
                return client;

            } catch (Exception e) {
                log.error("[识图] OCR 客户端初始化失败: {}", e.getMessage());
                client = null;
                return null;
            }
        }
    }
}
