package com.guardian.controller;

import com.guardian.ai.AiService;
import com.guardian.ai.LocalRuleProvider.AiContext;
import cn.hutool.core.util.StrUtil;
import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.common.ViewUtil;
import com.guardian.entity.Alert;
import com.guardian.entity.MonitorData;
import com.guardian.entity.User;
import com.guardian.mapper.AlertMapper;
import com.guardian.mapper.MonitorDataMapper;
import com.guardian.service.AuthService;
import com.guardian.service.HealthScoreService;
import com.guardian.service.ImageRecognizeService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康监测接口
 *
 * 路由：
 *   POST /api/monitor/text-analyze       文本健康分析（AI）
 *   POST /api/monitor/analyze-frame      图像/视频帧分析（AI）
 *   GET  /api/monitor/voice-data         语音数据列表
 *   GET  /api/monitor/video-data         视频数据列表
 *   POST /api/monitor/video-session-end  结束视频会话
 *   POST /api/monitor/alarm-record       记录告警（跌倒等）
 *   POST /api/monitor/alarm-confirm      确认/处理告警
 *   GET  /api/monitor/alarm-history/{userId} 告警历史
 *   POST /api/monitor/location-update    上报位置
 *   GET  /api/monitor/location-trace/{userId} 位置轨迹
 *   GET  /api/monitor/history/{userId}   监测数据历史
 *   GET  /api/monitor/report-data        报告数据
 *
 * ★ 语音/图像接口接收的是「描述性文本」或 base64 摘要，不做真实二进制解码，
 *   统一交给 AiService 做健康分析（Ollama → Coze → 规则库 三层降级）。
 */
@Slf4j
@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
public class MonitorController {

    private final MonitorDataMapper monitorMapper;
    private final AlertMapper alertMapper;
    private final AuthService authService;
    private final AiService aiService;
    private final HealthScoreService healthScoreService;
    private final ImageRecognizeService imageRecognizeService;

    /** 监测数据类型常量 */
    private static final String TYPE_STEP = "step";
    private static final String TYPE_HEART_RATE = "heart_rate";
    private static final String TYPE_LOCATION = "location";
    private static final String TYPE_VOICE = "voice";
    private static final String TYPE_VIDEO = "video";
    private static final String TYPE_IMAGE = "image";

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    // ========================================================================
    // ① AI 分析类
    // ========================================================================

    /**
     * 文本健康分析
     *
     * 请求体：{ "text": "今天头晕，血压有点高", "userId": "..." }
     */
    @PostMapping("/text-analyze")
    public ApiResponse<Map<String, Object>> textAnalyze(HttpServletRequest req,
                                                        @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));

        String text = ViewUtil.toStr(body == null ? null : body.get("text"));
        if (text == null) {
            throw BizException.badRequest("请提供要分析的文本内容");
        }
        if (text.length() > 2000) {
            throw BizException.badRequest("文本过长（最多 2000 字）");
        }

        String targetId = resolveTargetId(user, body);

        // 交给 AI 分析，并附带用户健康上下文
        AiService.ChatResult r = aiService.chat(buildHealthPrompt("文本描述", text), null, contextOf(targetId));

        // 分析结果落库，便于追溯
        saveMonitor(targetId, TYPE_VOICE, null, null, text, body);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("analysis", r.getContent());
        data.put("provider", r.getProvider());
        data.put("responseTime", r.getResponseTime());
        data.put("source", "text");
        data.put("level", riskLevel(text));
        data.put("analysedAt", LocalDateTime.now().toString());
        return ApiResponse.ok("分析完成", data);
    }

    /**
     * 图像 / 视频帧分析
     *
     * 请求体：
     *   { "description": "老人独自坐在地上，扶着沙发", "imageBase64": "...", "userId": "..." }
     *
     * ★ 真正的图像识别不在本服务做（没有视觉模型接入），
     *   接口接收「画面描述」文本，据此判断风险并给出建议。
     *   同时检测描述中是否包含跌倒特征词，命中则自动产生 fall 告警。
     */
    @PostMapping("/analyze-frame")
    public ApiResponse<Map<String, Object>> analyzeFrame(HttpServletRequest req,
                                                         @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));

        String description = ViewUtil.toStr(body == null ? null : body.get("description"));
        String imageBase64 = ViewUtil.toStr(body == null ? null : body.get("imageBase64"));

        // 没有描述但有 base64 时，只做长度摘要，不真解码
        if (description == null && imageBase64 == null) {
            throw BizException.badRequest("请提供画面描述（description）或图像数据（imageBase64）");
        }

        String targetId = resolveTargetId(user, body);

        String prompt;
        if (description != null) {
            prompt = buildHealthPrompt("画面描述", description);
        } else {
            // 仅凭 base64 无法识别内容，给出数据量级供人工判断
            prompt = buildHealthPrompt("画面数据", "收到一段图像数据，长度约 " + imageBase64.length() + " 字符，请给出通用居家安全建议。");
        }

        AiService.ChatResult r = aiService.chat(prompt, null, contextOf(targetId));

        // 跌倒特征词检测
        boolean fallSuspected = description != null && containsAny(description, FALL_KEYWORDS);

        String alertId = null;
        if (fallSuspected) {
            alertId = createAlert(targetId, "fall", "critical",
                    "疑似跌倒", "图像分析发现跌倒特征：" + description);
            log.warn("【跌倒检测】用户 {} 画面疑似跌倒：{}", targetId, description);
        }

        saveMonitor(targetId, TYPE_VIDEO, null, null, description, body);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("analysis", r.getContent());
        data.put("provider", r.getProvider());
        data.put("responseTime", r.getResponseTime());
        data.put("source", "image");
        data.put("fallSuspected", fallSuspected);
        data.put("alertId", alertId);
        data.put("tips", fallSuspected
                ? "检测到跌倒风险，请立即确认长辈状态，必要时点击 SOS 或拨打 120"
                : "画面未见明显异常");
        data.put("analysedAt", LocalDateTime.now().toString());

        return ApiResponse.ok(fallSuspected ? "检测到跌倒风险" : "分析完成", data);
    }

    /**
     * 跌倒检测（独立入口）
     *
     * 请求体：{ "confidence": 0.92, "location": "客厅", "description": "...", "userId": "..." }
     *
     * ★ 入库为 alert，alert_type = 'fall'
     */
    @PostMapping("/fall-detect")
    public ApiResponse<Map<String, Object>> fallDetect(HttpServletRequest req,
                                                       @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));
        String targetId = resolveTargetId(user, body);

        double confidence = ViewUtil.toDouble(body == null ? null : body.get("confidence"), 0.8);
        String location = ViewUtil.toStr(body == null ? null : body.get("location"), "未知位置");
        String description = ViewUtil.toStr(body == null ? null : body.get("description"));

        // 置信度低于阈值只记录不告警，避免误报打扰家属
        boolean confirmed = confidence >= 0.6;
        String severity = confidence >= 0.85 ? "critical" : confirmed ? "high" : "low";

        String detail = String.format("跌倒检测置信度 %.0f%%，位置：%s%s",
                confidence * 100, location,
                description == null ? "" : "，描述：" + description);

        String alertId = createAlert(targetId, "fall", severity,
                confirmed ? "跌倒警报" : "疑似跌倒（低置信度）", detail);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("alertId", alertId);
        data.put("confirmed", confirmed);
        data.put("confidence", confidence);
        data.put("severity", severity);
        data.put("location", location);
        data.put("occurredAt", LocalDateTime.now().toString());
        data.put("message", confirmed
                ? "已生成跌倒告警，请尽快联系长辈确认情况"
                : "置信度较低，已记录但未升级为紧急告警");
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // ② 语音 / 视频
    // ========================================================================

    /**
     * 语音数据分析
     *
     * 请求体：{ "text": "语音转写内容", "userId": "...", "duration": 12 }
     *
     * ★ 与 text-analyze 的区别：结果以 voice 类型入库，且可携带时长与音频摘要。
     */
    @PostMapping("/voice-analyze")
    public ApiResponse<Map<String, Object>> voiceAnalyze(HttpServletRequest req,
                                                         @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));

        String text = ViewUtil.toStr(body == null ? null : body.get("text"));
        String audioBase64 = ViewUtil.toStr(body == null ? null : body.get("audioBase64"));

        if (text == null && audioBase64 == null) {
            throw BizException.badRequest("请提供语音转写文本（text）或音频数据（audioBase64）");
        }

        String targetId = resolveTargetId(user, body);
        Double duration = ViewUtil.toDouble(body == null ? null : body.get("duration"));

        String prompt = text != null
                ? buildHealthPrompt("语音描述", text)
                : buildHealthPrompt("语音数据", "收到一段语音数据，长度约 " + audioBase64.length() + " 字符。");

        AiService.ChatResult r = aiService.chat(prompt, null, contextOf(targetId));

        saveMonitor(targetId, TYPE_VOICE, duration, "秒", text, body);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("analysis", r.getContent());
        data.put("provider", r.getProvider());
        data.put("responseTime", r.getResponseTime());
        data.put("source", "voice");
        data.put("transcript", text);
        data.put("duration", duration);
        data.put("analysedAt", LocalDateTime.now().toString());
        return ApiResponse.ok("语音分析完成", data);
    }

    /** 语音数据列表（分页） */
    @GetMapping("/voice-data")
    public ApiResponse<Map<String, Object>> voiceData(HttpServletRequest req,
                                                      @RequestParam(required = false) String userId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int pageSize) {
        User me = authService.requireUser(token(req));
        String targetId = blankToNull(userId) == null ? me.getId() : userId;

        return ApiResponse.ok(listMonitor(targetId, TYPE_VOICE, page, pageSize));
    }

    /** 视频数据列表（分页） */
    @GetMapping("/video-data")
    public ApiResponse<Map<String, Object>> videoData(HttpServletRequest req,
                                                      @RequestParam(required = false) String userId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "20") int pageSize) {
        User me = authService.requireUser(token(req));
        String targetId = blankToNull(userId) == null ? me.getId() : userId;

        return ApiResponse.ok(listMonitor(targetId, TYPE_VIDEO, page, pageSize));
    }

    /**
     * 图像分析
     *
     * 请求体：{ "description": "老人在厨房做饭", "image": "base64（可选）", "userId": "..." }
     *
     * ★ 小程序端传的是「图片描述」（把拍到的东西用文字讲给 AI），
     *   也可以带上 image（base64）走腾讯云 OCR 先读字再分析。
     *   两种都支持，前端不用改。
     *
     * 与 analyze-frame 的区别：这个以 image 类型入库，便于「视频监测」页回看历史。
     */
    @PostMapping({"/image-analyze", "/imageAnalyze"})
    public ApiResponse<Map<String, Object>> imageAnalyze(HttpServletRequest req,
                                                         @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));

        String description = ViewUtil.toStr(body == null ? null : body.get("description"));
        String imageBase64 = ViewUtil.toStr(body == null ? null : body.get("image"));

        if (StrUtil.isBlank(description) && StrUtil.isBlank(imageBase64)) {
            throw BizException.badRequest("请提供图片描述（description）或图片数据（image）");
        }

        String targetId = resolveTargetId(user, body);

        Map<String, Object> result;
        String sourceText;

        if (StrUtil.isNotBlank(imageBase64)) {
            // 有图 → OCR 抽字 + AI 解释
            result = imageRecognizeService.analyzeImage(
                    imageBase64, description, targetId, contextOf(targetId));
            sourceText = description;
        } else {
            // 只有描述 → 直接交给 AI
            String prompt = buildHealthPrompt("图像描述", description);
            AiService.ChatResult r = aiService.chat(prompt, null, contextOf(targetId));
            result = new LinkedHashMap<>();
            result.put("analysis", r.getContent());
            result.put("provider", r.getProvider());
            result.put("responseTime", r.getResponseTime());
            sourceText = description;
        }

        saveMonitor(targetId, TYPE_IMAGE, null, null, sourceText, body);

        Map<String, Object> data = new LinkedHashMap<>(result);
        data.put("source", "image");
        data.put("description", description);
        data.put("hasImage", StrUtil.isNotBlank(imageBase64));
        data.put("analysedAt", LocalDateTime.now().toString());
        return ApiResponse.ok("图像分析完成", data);
    }

    /**
     * 结束视频会话
     *
     * 请求体：{ "sessionId": "...", "duration": 300, "userId": "..." }
     *
     * 把本次会话的时长与摘要以 video 类型落库。
     */
    @PostMapping("/video-session-end")
    public ApiResponse<Map<String, Object>> endVideoSession(HttpServletRequest req,
                                                            @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));
        String targetId = resolveTargetId(user, body);

        String sessionId = ViewUtil.toStr(body == null ? null : body.get("sessionId"), CommonUtil.uuid());
        Double duration = ViewUtil.toDouble(body == null ? null : body.get("duration"), 0);

        MonitorData d = new MonitorData();
        d.setUserId(targetId);
        d.setDeviceId(ViewUtil.toStr(body == null ? null : body.get("deviceId")));
        d.setDataType(TYPE_VIDEO);
        d.setValue(BigDecimal.valueOf(duration));
        d.setUnit("秒");
        d.setRecordedAt(LocalDateTime.now());
        d.setMetadata(toJson(ViewUtil.map("sessionId", sessionId, "event", "session_end")));
        monitorMapper.insert(d);

        log.info("视频会话 {} 结束，用户 {}，时长 {} 秒", sessionId, targetId, duration);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", sessionId);
        data.put("duration", duration);
        data.put("endedAt", LocalDateTime.now().toString());
        data.put("saved", true);
        return ApiResponse.ok("视频会话已结束", data);
    }

    // ========================================================================
    // ③ 告警
    // ========================================================================

    /**
     * 记录告警
     *
     * 请求体：{ "alertType": "fall", "severity": "high", "title": "...", "description": "...", "userId": "..." }
     */
    @PostMapping("/alarm-record")
    public ApiResponse<Map<String, Object>> alarmRecord(HttpServletRequest req,
                                                        @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));
        String targetId = resolveTargetId(user, body);

        String type = ViewUtil.toStr(body == null ? null : body.get("alertType"), "abnormal");
        String severity = ViewUtil.toStr(body == null ? null : body.get("severity"), "medium");
        String title = ViewUtil.toStr(body == null ? null : body.get("title"), typeTitle(type));
        String description = ViewUtil.toStr(body == null ? null : body.get("description"));

        String alertId = createAlert(targetId, type, severity, title, description);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", alertId);
        data.put("alertType", type);
        data.put("severity", severity);
        data.put("occurredAt", LocalDateTime.now().toString());
        return ApiResponse.ok("告警已记录", data);
    }

    /**
     * 确认 / 处理告警
     *
     * 请求体：{ "id": "告警ID", "notes": "已电话确认，无大碍" }
     */
    @PostMapping("/alarm-confirm")
    public ApiResponse<Map<String, Object>> alarmConfirm(HttpServletRequest req,
                                                         @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));

        String alertId = ViewUtil.toStr(body == null ? null : body.get("id"));
        if (alertId == null) {
            alertId = ViewUtil.toStr(body == null ? null : body.get("alertId"));
        }
        if (alertId == null) {
            throw BizException.badRequest("缺少告警 ID");
        }

        Alert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw BizException.notFound("告警不存在");
        }
        if (Boolean.TRUE.equals(alert.getHandled())) {
            throw BizException.badRequest("该告警已处理过了");
        }

        String notes = ViewUtil.toStr(body.get("notes"), "已确认");
        alertMapper.updateHandled(alertId, me.getId(), notes);
        log.info("监测告警 {} 已由 {} 确认", alertId, me.getId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("id", alertId);
        data.put("handledBy", me.getId());
        data.put("handledAt", LocalDateTime.now().toString());
        data.put("notes", notes);
        return ApiResponse.ok("告警已确认", data);
    }

    /** 告警历史（按用户ID取路径参数） */
    @GetMapping("/alarm-history/{userId}")
    public ApiResponse<Map<String, Object>> alarmHistory(HttpServletRequest req,
                                                         @PathVariable String userId,
                                                         @RequestParam(required = false) Boolean handled,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "20") int pageSize) {
        authService.requireUser(token(req));

        int p = ViewUtil.clamp(page, 1, 100000);
        int size = ViewUtil.clamp(pageSize, 1, 100);
        int offset = (p - 1) * size;

        List<Alert> rows = alertMapper.selectPage(userId, handled, offset, size);
        long total = alertMapper.countByUser(userId, handled);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Alert a : rows) {
            list.add(toAlertView(a));
        }

        return ApiResponse.ok(ViewUtil.page(list, total, p, size));
    }

    // ========================================================================
    // ④ 位置
    // ========================================================================

    /**
     * 上报位置
     *
     * 请求体：{ "lat": 39.9, "lng": 116.4, "address": "北京市朝阳区", "userId": "..." }
     *
     * 位置以 dataType=location 落库，value 存经度便于聚合，完整坐标放 metadata。
     */
    @PostMapping("/location-update")
    public ApiResponse<Map<String, Object>> updateLocation(HttpServletRequest req,
                                                           @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));
        String targetId = resolveTargetId(user, body);

        Double lat = ViewUtil.toDouble(body == null ? null : body.get("lat"));
        Double lng = ViewUtil.toDouble(body == null ? null : body.get("lng"));
        if (lat == null || lng == null) {
            throw BizException.badRequest("缺少经纬度（lat / lng）");
        }
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw BizException.badRequest("经纬度超出有效范围");
        }

        String address = ViewUtil.toStr(body.get("address"));
        Double accuracy = ViewUtil.toDouble(body.get("accuracy"));

        MonitorData d = new MonitorData();
        d.setUserId(targetId);
        d.setDeviceId(ViewUtil.toStr(body.get("deviceId")));
        d.setDataType(TYPE_LOCATION);
        d.setValue(BigDecimal.valueOf(lng));
        d.setUnit("度");
        d.setRecordedAt(LocalDateTime.now());
        d.setMetadata(toJson(ViewUtil.map(
                "lat", lat,
                "lng", lng,
                "address", address,
                "accuracy", accuracy
        )));
        monitorMapper.insert(d);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", d.getId());
        data.put("lat", lat);
        data.put("lng", lng);
        data.put("address", address);
        data.put("accuracy", accuracy);
        data.put("recordedAt", d.getRecordedAt().toString());
        return ApiResponse.ok("位置已更新", data);
    }

    /** 位置轨迹（默认最近 24 小时，最多 500 个点） */
    @GetMapping("/location-trace/{userId}")
    public ApiResponse<Map<String, Object>> locationTrace(HttpServletRequest req,
                                                          @PathVariable String userId,
                                                          @RequestParam(defaultValue = "24") int hours,
                                                          @RequestParam(defaultValue = "500") int limit) {
        authService.requireUser(token(req));

        int h = ViewUtil.clamp(hours, 1, 24 * 30);
        int lim = ViewUtil.clamp(limit, 1, 2000);

        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusHours(h);

        List<MonitorData> rows = monitorMapper.selectByRange(userId, TYPE_LOCATION, from, to, lim);

        // 查询是倒序的，轨迹要按时间正序返回
        List<Map<String, Object>> points = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            MonitorData d = rows.get(i);
            Map<String, Object> meta = parseJson(d.getMetadata());

            Map<String, Object> p = new LinkedHashMap<>();
            p.put("id", d.getId());
            p.put("lat", meta.get("lat"));
            p.put("lng", meta.get("lng"));
            p.put("address", meta.get("address"));
            p.put("accuracy", meta.get("accuracy"));
            p.put("recordedAt", ViewUtil.dateTimeStr(d.getRecordedAt()));
            points.add(p);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", points);
        data.put("total", points.size());
        data.put("from", from.toString());
        data.put("to", to.toString());
        data.put("hours", h);
        data.put("hasMore", rows.size() >= lim);
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // ⑤ 历史 / 报告
    // ========================================================================

    /**
     * 监测数据历史
     *
     * 路径参数 userId；可选 dataType 过滤、days 指定回溯天数。
     */
    @GetMapping("/history/{userId}")
    public ApiResponse<Map<String, Object>> history(HttpServletRequest req,
                                                    @PathVariable String userId,
                                                    @RequestParam(required = false) String dataType,
                                                    @RequestParam(defaultValue = "7") int days,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int pageSize) {
        authService.requireUser(token(req));

        int p = ViewUtil.clamp(page, 1, 100000);
        int size = ViewUtil.clamp(pageSize, 1, 100);
        int d = ViewUtil.clamp(days, 1, 365);

        int offset = (p - 1) * size;
        List<MonitorData> rows = monitorMapper.selectPage(userId, blankToNull(dataType), offset, size);
        long total = monitorMapper.countByUserAndType(userId, blankToNull(dataType));

        List<Map<String, Object>> list = new ArrayList<>();
        for (MonitorData m : rows) {
            list.add(toMonitorView(m));
        }

        Map<String, Object> data = ViewUtil.page(list, total, p, size);
        data.put("dataType", blankToNull(dataType));
        data.put("days", d);
        return ApiResponse.ok(data);
    }

    /**
     * 监测统计
     *
     * GET /api/monitor/stats
     *
     * 汇总各数据类型的条数与最新值，并给出最新监测数据快照。
     */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(HttpServletRequest req,
                                                  @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));
        String targetId = blankToNull(userId) == null ? me.getId() : userId;

        List<Map<String, Object>> byType = monitorMapper.statsByType(targetId);

        List<Map<String, Object>> types = new ArrayList<>();
        long totalCount = 0;
        for (Map<String, Object> row : byType) {
            long cnt = ViewUtil.toLong(row.get("cnt"), 0);
            totalCount += cnt;

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dataType", row.get("dataType"));
            m.put("dataTypeText", typeLabel(ViewUtil.str(row.get("dataType"))));
            m.put("count", cnt);
            m.put("lastAt", ViewUtil.str(row.get("lastAt")));
            types.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", targetId);
        data.put("totalCount", totalCount);
        data.put("typeCount", types.size());
        data.put("types", types);
        data.put("latest", latestSnapshot(targetId));
        data.put("generatedAt", LocalDateTime.now().toString());
        return ApiResponse.ok(data);
    }

    /**
     * 最新监测数据
     *
     * GET /api/monitor/latest
     */
    @GetMapping("/latest")
    public ApiResponse<Map<String, Object>> latest(HttpServletRequest req,
                                                   @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));
        String targetId = blankToNull(userId) == null ? me.getId() : userId;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", targetId);
        data.put("items", latestSnapshot(targetId));
        data.put("generatedAt", LocalDateTime.now().toString());
        return ApiResponse.ok(data);
    }

    /**
     * 报告数据
     *
     * 汇总健康评分 + 近 7 天监测概况 + 告警统计，供报告页一次性拉取。
     */
    @GetMapping("/report-data")
    public ApiResponse<Map<String, Object>> reportData(HttpServletRequest req,
                                                       @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));
        String targetId = blankToNull(userId) == null ? me.getId() : userId;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusDays(7);

        // ---------- 健康评分 ----------
        Map<String, Object> scoreView = new LinkedHashMap<>();
        try {
            HealthScoreService.HealthScore s = healthScoreService.calculate(targetId);
            scoreView.put("totalScore", s.getTotalScore());
            scoreView.put("level", s.getLevel());
            scoreView.put("suggestion", s.getSuggestion());
            scoreView.put("sleep", s.getSleepScore());
            scoreView.put("medicine", s.getMedicineScore());
            scoreView.put("exercise", s.getExerciseScore());
            scoreView.put("diet", s.getDietScore());
            scoreView.put("monitor", s.getMonitorScore());
        } catch (Exception e) {
            log.warn("计算健康评分失败（报告降级）: {}", e.getMessage());
            scoreView.put("totalScore", null);
            scoreView.put("error", "评分计算失败");
        }

        // ---------- 近 7 天告警 ----------
        List<Alert> alerts = alertMapper.selectByUserAndRange(targetId, weekAgo, now);
        long unhandled = alerts.stream().filter(a -> Boolean.FALSE.equals(a.getHandled())).count();

        Map<String, Object> alertStats = new LinkedHashMap<>();
        alertStats.put("weekTotal", alerts.size());
        alertStats.put("weekUnhandled", unhandled);
        Map<String, Long> byType = new LinkedHashMap<>();
        for (Alert a : alerts) {
            String t = a.getAlertType() == null ? "unknown" : a.getAlertType();
            byType.merge(t, 1L, Long::sum);
        }
        alertStats.put("byType", byType);

        // ---------- 监测数据 ----------
        long monitorCount = monitorMapper.countByUserAndType(targetId, null);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", targetId);
        data.put("generatedAt", now.toString());
        data.put("range", ViewUtil.map("from", weekAgo.toString(), "to", now.toString()));
        data.put("healthScore", scoreView);
        data.put("alerts", alertStats);
        data.put("monitorCount", monitorCount);
        data.put("latest", latestSnapshot(targetId));
        data.put("recentAlerts", recentAlerts(targetId, alerts));
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // 内部工具
    // ========================================================================

    /** 分页查询某类型监测数据并转视图 */
    private Map<String, Object> listMonitor(String userId, String dataType, int page, int pageSize) {
        int p = ViewUtil.clamp(page, 1, 100000);
        int size = ViewUtil.clamp(pageSize, 1, 100);

        List<MonitorData> rows = monitorMapper.selectPage(userId, dataType, (p - 1) * size, size);
        long total = monitorMapper.countByUserAndType(userId, dataType);

        List<Map<String, Object>> list = new ArrayList<>();
        for (MonitorData m : rows) {
            list.add(toMonitorView(m));
        }

        Map<String, Object> data = ViewUtil.page(list, total, p, size);
        data.put("dataType", dataType);
        return data;
    }

    /** 转监测数据视图 */
    private Map<String, Object> toMonitorView(MonitorData m) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId());
        v.put("userId", m.getUserId());
        v.put("deviceId", m.getDeviceId());
        v.put("dataType", m.getDataType());
        v.put("dataTypeText", typeLabel(m.getDataType()));
        v.put("value", m.getValue());
        v.put("unit", m.getUnit());
        v.put("recordedAt", ViewUtil.dateTimeStr(m.getRecordedAt()));
        v.put("metadata", parseJson(m.getMetadata()));
        return v;
    }

    /** 转告警视图 */
    private Map<String, Object> toAlertView(Alert a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("userId", a.getUserId());
        m.put("type", a.getAlertType());
        m.put("typeText", typeLabel(a.getAlertType()));
        m.put("severity", a.getSeverity());
        m.put("severityText", severityLabel(a.getSeverity()));
        m.put("title", a.getTitle());
        m.put("description", a.getDescription());
        m.put("occurredAt", ViewUtil.dateTimeStr(a.getOccurredAt()));
        m.put("handled", a.getHandled());
        m.put("handledBy", a.getHandledBy());
        m.put("handledAt", ViewUtil.dateTimeStr(a.getHandledAt()));
        m.put("notes", a.getNotes());
        return m;
    }

    /** 最近 5 条告警视图 */
    private List<Map<String, Object>> recentAlerts(String userId, List<Alert> alerts) {
        List<Map<String, Object>> out = new ArrayList<>();
        int n = Math.min(5, alerts.size());
        for (int i = 0; i < n; i++) {
            out.add(toAlertView(alerts.get(i)));
        }
        return out;
    }

    /** 每种数据类型的最新值快照 */
    private List<Map<String, Object>> latestSnapshot(String userId) {
        List<Map<String, Object>> rows = monitorMapper.selectLatestByType(userId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            String type = ViewUtil.str(row.get("dataType"));
            m.put("dataType", type);
            m.put("dataTypeText", typeLabel(type));
            m.put("value", row.get("value"));
            m.put("unit", row.get("unit"));
            m.put("deviceId", row.get("deviceId"));
            m.put("recordedAt", ViewUtil.str(row.get("recordedAt")));
            out.add(m);
        }
        return out;
    }

    /** 落库一条监测数据，失败不影响主流程 */
    private void saveMonitor(String userId, String dataType, Double value, String unit,
                             String text, Map<String, Object> body) {
        try {
            MonitorData d = new MonitorData();
            d.setUserId(userId);
            d.setDeviceId(body == null ? null : ViewUtil.toStr(body.get("deviceId")));
            d.setDataType(dataType);
            d.setValue(value == null ? null : BigDecimal.valueOf(value));
            d.setUnit(unit);
            d.setRecordedAt(LocalDateTime.now());
            d.setMetadata(toJson(ViewUtil.map(
                    "text", text == null ? null : truncate(text, 1000),
                    "location", body == null ? null : ViewUtil.toStr(body.get("location"))
            )));
            monitorMapper.insert(d);
        } catch (Exception e) {
            log.warn("监测数据落库失败（不影响分析结果）: {}", e.getMessage());
        }
    }

    /** 生成告警并返回 ID */
    private String createAlert(String userId, String type, String severity, String title, String description) {
        Alert alert = new Alert();
        alert.setId(CommonUtil.uuid());
        alert.setUserId(userId);
        alert.setAlertType(type);
        alert.setSeverity(severity);
        alert.setTitle(title);
        alert.setDescription(description);
        alert.setOccurredAt(LocalDateTime.now());
        alert.setHandled(false);
        alertMapper.insert(alert);
        return alert.getId();
    }

    /** 取目标用户：默认自己，允许家属传 userId 查看长辈 */
    private String resolveTargetId(User me, Map<String, Object> body) {
        String uid = body == null ? null : blankToNull(ViewUtil.toStr(body.get("userId")));
        return uid == null ? me.getId() : uid;
    }

    /** 组装 AI 健康上下文 */
    private AiContext contextOf(String userId) {
        AiContext ctx = new AiContext();
        try {
            ctx.setHealthScore(healthScoreService.calculate(userId).getTotalScore());
        } catch (Exception e) {
            log.debug("健康评分计算失败，AI 上下文降级: {}", e.getMessage());
        }
        try {
            MonitorData steps = monitorMapper.selectLatestOne(userId, TYPE_STEP);
            if (steps != null && steps.getValue() != null) {
                ctx.setTodaySteps(steps.getValue().intValue());
            }
        } catch (Exception e) {
            log.debug("步数查询失败: {}", e.getMessage());
        }
        return ctx;
    }

    /** 拼接健康分析提示词 */
    private String buildHealthPrompt(String kind, String content) {
        return String.format("""
                请作为老年人健康守护助手，分析下面这段%s，给出：
                1) 可能的健康风险（如有）
                2) 给家属的处置建议（1-2 条）
                3) 是否需要立即就医或呼叫 SOS
                回复控制在 200 字以内，语言通俗，不要做医疗诊断。

                【%s】%s""", kind, kind, content);
    }

    /** 简单风险分级：命中高危词为 high，命中关注词为 medium */
    private String riskLevel(String text) {
        if (text == null) return "low";
        if (containsAny(text, HIGH_RISK_KEYWORDS)) return "high";
        if (containsAny(text, MID_RISK_KEYWORDS)) return "medium";
        return "low";
    }

    private boolean containsAny(String text, String[] keywords) {
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    /** 跌倒特征词 */
    private static final String[] FALL_KEYWORDS = {
            "跌倒", "摔倒", "倒地", "趴在地上", "躺在地上", "坐在地上", "摔了", "滑倒", "晕倒"
    };

    /** 高危健康词 */
    private static final String[] HIGH_RISK_KEYWORDS = {
            "胸痛", "胸闷", "呼吸困难", "喘不上气", "晕倒", "昏迷", "抽搐", "大出血",
            "说不出话", "嘴歪", "半身", "偏瘫", "剧烈头痛", "意识不清"
    };

    /** 中等风险健康词 */
    private static final String[] MID_RISK_KEYWORDS = {
            "头晕", "头痛", "血压", "心慌", "心悸", "乏力", "没力气", "恶心", "呕吐",
            "失眠", "疼", "肿", "咳嗽", "发烧", "腹泻"
    };

    private String typeLabel(String type) {
        if (type == null) return "未知";
        return switch (type) {
            case TYPE_HEART_RATE -> "心率";
            case TYPE_STEP -> "步数";
            case TYPE_LOCATION -> "位置";
            case "sleep" -> "睡眠";
            case "spo2" -> "血氧";
            case "blood_pressure" -> "血压";
            case "temperature" -> "体温";
            case TYPE_VOICE -> "语音";
            case TYPE_VIDEO -> "视频";
            case "fall" -> "跌倒";
            case "sos" -> "紧急求助";
            case "abnormal" -> "健康异常";
            case "feedback" -> "用户反馈";
            default -> type;
        };
    }

    private String severityLabel(String s) {
        if (s == null) return "提示";
        return switch (s) {
            case "critical" -> "紧急";
            case "high" -> "严重";
            case "medium" -> "注意";
            default -> "提示";
        };
    }

    private String typeTitle(String type) {
        if (type == null) return "健康异常";
        return switch (type) {
            case "fall" -> "跌倒警报";
            case "sos" -> "紧急求助";
            case TYPE_HEART_RATE -> "心率异常";
            default -> "健康异常";
        };
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Map 转 JSON 字符串（存 JSON 列），失败返回 null */
    private String toJson(Map<String, Object> m) {
        if (m == null) return null;
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (e.getValue() == null) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append('"').append(escape(String.valueOf(v))).append('"');
            }
        }
        return sb.append('}').toString();
    }

    /** 解析 JSON 列（宽松实现：只处理一层键值对） */
    private Map<String, Object> parseJson(String json) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (json == null || json.isBlank()) return m;

        String s = json.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);

        int i = 0;
        while (i < s.length()) {
            int ks = s.indexOf('"', i);
            if (ks < 0) break;
            int ke = s.indexOf('"', ks + 1);
            if (ke < 0) break;
            String key = unescape(s.substring(ks + 1, ke));

            int colon = s.indexOf(':', ke);
            if (colon < 0) break;

            int vs = colon + 1;
            while (vs < s.length() && s.charAt(vs) == ' ') vs++;

            Object value;
            int next;
            if (vs < s.length() && s.charAt(vs) == '"') {
                int ve = s.indexOf('"', vs + 1);
                if (ve < 0) break;
                value = unescape(s.substring(vs + 1, ve));
                next = ve + 1;
            } else {
                int ve = s.indexOf(',', vs);
                if (ve < 0) ve = s.length();
                String raw = s.substring(vs, ve).trim();
                value = parseScalar(raw);
                next = ve;
            }

            m.put(key, value);

            int comma = s.indexOf(',', next);
            if (comma < 0) break;
            i = comma + 1;
        }
        return m;
    }

    /** 标量解析：数字 / 布尔 / null / 字符串 */
    private Object parseScalar(String raw) {
        if (raw == null || raw.isEmpty() || "null".equals(raw)) return null;
        if ("true".equalsIgnoreCase(raw)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(raw)) return Boolean.FALSE;
        try {
            return Double.parseDouble(raw);
        } catch (Exception e) {
            return raw;
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /** 本地日期边界（供扩展使用） */
    @SuppressWarnings("unused")
    private LocalDateTime startOfDay(LocalDate d) {
        return d.atStartOfDay();
    }
}
