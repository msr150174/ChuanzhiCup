package com.guardian.controller;

import com.guardian.ai.LocalRuleProvider.AiContext;
import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.entity.User;
import com.guardian.service.AuthService;
import com.guardian.service.HealthScoreService;
import com.guardian.service.ImageRecognizeService;
import com.guardian.service.TencentMapService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 地图 + 识图接口
 *
 * 地图（腾讯位置服务）：
 *   POST /api/map/regeo           坐标转地址
 *   POST /api/map/geocode         地址转坐标
 *   GET  /api/map/nearby          周边搜索（医院 / 药店）
 *   POST /api/map/distance        两点距离
 *
 * 识图：
 *   POST /api/image/ocr           纯文字识别（药品说明书 / 体检单）
 *   POST /api/image/analyze       识图 + AI 解释
 *   POST /api/image/fall-detect   跌倒风险判断
 *   GET  /api/image/quota         今日剩余次数
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class MapImageController {

    private final TencentMapService mapService;
    private final ImageRecognizeService imageService;
    private final AuthService authService;
    private final HealthScoreService healthScoreService;

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    private User me(HttpServletRequest req) {
        return authService.requireUser(token(req));
    }

    /** 组装健康上下文，让 AI 回答更贴合用户当前状态 */
    private AiContext buildContext(User user) {
        AiContext ctx = new AiContext();
        try {
            HealthScoreService.HealthScore s = healthScoreService.calculate(user.getId());
            ctx.setHealthScore(s.getTotalScore());
        } catch (Exception e) {
            log.debug("健康评分计算失败: {}", e.getMessage());
        }
        return ctx;
    }

    // ========================================================================
    // 地图
    // ========================================================================

    /**
     * 坐标转地址
     *
     * 请求体：{ "lat": 39.909, "lng": 116.397 }
     */
    @PostMapping("/map/regeo")
    public ApiResponse<Map<String, Object>> regeo(@RequestBody Map<String, Object> body) {
        double lat = num(body, "lat");
        double lng = num(body, "lng");
        if (lat == 0 && lng == 0) {
            throw BizException.badRequest("缺少坐标");
        }

        Map<String, Object> data = mapService.reverseGeocode(lat, lng);
        data.put("mapConfigured", mapService.isConfigured());
        return ApiResponse.ok(data);
    }

    /**
     * 地址转坐标
     *
     * 请求体：{ "address": "北京市朝阳区xxx" }
     */
    @PostMapping("/map/geocode")
    public ApiResponse<Map<String, Object>> geocode(@RequestBody Map<String, Object> body) {
        String address = body == null ? null : str(body.get("address"));
        if (address == null || address.isBlank()) {
            throw BizException.badRequest("请输入地址");
        }

        Map<String, Object> data = mapService.geocode(address);
        data.put("mapConfigured", mapService.isConfigured());
        return ApiResponse.ok(data);
    }

    /**
     * 周边搜索
     *
     * GET /api/map/nearby?keyword=医院&lat=39.9&lng=116.4&radius=2000
     */
    @GetMapping("/map/nearby")
    public ApiResponse<Map<String, Object>> nearby(
            @RequestParam(defaultValue = "医院") String keyword,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "2000") int radius) {

        int r = Math.max(100, Math.min(radius, 10000));
        List<Map<String, Object>> list = mapService.searchNearby(keyword, lat, lng, r);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("keyword", keyword);
        data.put("radius", r);
        data.put("center", Map.of("lat", lat, "lng", lng));
        data.put("list", list);
        data.put("total", list.size());
        data.put("mapConfigured", mapService.isConfigured());

        if (list.isEmpty() && !mapService.isConfigured()) {
            data.put("hint", "未配置腾讯地图 Key，周边搜索不可用");
        }
        return ApiResponse.ok(data);
    }

    /**
     * 两点距离
     *
     * 本地 Haversine 计算，不消耗地图配额。
     */
    @PostMapping("/map/distance")
    public ApiResponse<Map<String, Object>> distance(@RequestBody Map<String, Object> body) {
        double lat1 = num(body, "lat1");
        double lng1 = num(body, "lng1");
        double lat2 = num(body, "lat2");
        double lng2 = num(body, "lng2");

        double meters = TencentMapService.distance(lat1, lng1, lat2, lng2);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("meters", Math.round(meters));
        data.put("text", TencentMapService.distanceText(meters));
        data.put("from", Map.of("lat", lat1, "lng", lng1));
        data.put("to", Map.of("lat", lat2, "lng", lng2));
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // 识图
    // ========================================================================

    /**
     * 纯文字识别
     *
     * 请求体：{ "image": "data:image/png;base64,xxxx" 或纯 base64 }
     */
    @PostMapping("/image/ocr")
    public ApiResponse<Map<String, Object>> ocr(HttpServletRequest req,
                                                @RequestBody Map<String, Object> body) {
        User user = me(req);
        String image = body == null ? null : str(body.get("image"));
        if (image == null || image.isBlank()) {
            throw BizException.badRequest("请上传图片");
        }

        Map<String, Object> data = imageService.recognizeText(image, user.getId());
        data.put("ocrConfigured", imageService.isOcrConfigured());
        data.put("remainingToday", imageService.remainingToday(user.getId()));
        return ApiResponse.ok(data);
    }

    /**
     * 识图 + AI 解释
     *
     * 请求体：{ "image": "base64...", "question": "这个药怎么吃" }
     *
     * ★ 这是「拍药盒 / 拍体检单」的主接口：
     *   先 OCR 抽出文字，再把文字交给 AI 用通俗语言解释。
     */
    @PostMapping("/image/analyze")
    public ApiResponse<Map<String, Object>> analyze(HttpServletRequest req,
                                                    @RequestBody Map<String, Object> body) {
        User user = me(req);
        String image = body == null ? null : str(body.get("image"));
        String question = body == null ? null : str(body.get("question"));

        if ((image == null || image.isBlank()) && (question == null || question.isBlank())) {
            throw BizException.badRequest("请上传图片或描述你的问题");
        }

        AiContext ctx = buildContext(user);
        Map<String, Object> data = imageService.analyzeImage(image, question, user.getId(), ctx);
        data.put("remainingToday", imageService.remainingToday(user.getId()));
        return ApiResponse.ok(data);
    }

    /**
     * 跌倒风险判断
     *
     * 请求体：{ "image": "base64...", "description": "老人躺在客厅地上" }
     * 图片和描述至少给一个。
     */
    @PostMapping("/image/fall-detect")
    public ApiResponse<Map<String, Object>> fallDetect(HttpServletRequest req,
                                                       @RequestBody Map<String, Object> body) {
        User user = me(req);
        String image = body == null ? null : str(body.get("image"));
        String description = body == null ? null : str(body.get("description"));

        if ((image == null || image.isBlank()) && (description == null || description.isBlank())) {
            throw BizException.badRequest("请上传图片或描述现场情况");
        }

        AiContext ctx = buildContext(user);
        Map<String, Object> data = imageService.detectFall(image, description, user.getId(), ctx);
        return ApiResponse.ok(data);
    }

    /** 今日剩余识别次数 */
    @GetMapping("/image/quota")
    public ApiResponse<Map<String, Object>> quota(HttpServletRequest req) {
        User user = me(req);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("remainingToday", imageService.remainingToday(user.getId()));
        data.put("ocrConfigured", imageService.isOcrConfigured());
        data.put("mapConfigured", mapService.isConfigured());
        return ApiResponse.ok(data);
    }

    // ---------------- 工具 ----------------

    private double num(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return 0;
        }
        try {
            return Double.parseDouble(String.valueOf(body.get(key)));
        } catch (Exception e) {
            return 0;
        }
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
