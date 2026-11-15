package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.entity.User;
import com.guardian.service.AuthService;
import com.guardian.service.DietService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 饮食接口
 *
 * 路由（context-path 已带 /api）：
 *   POST /api/diet/record       新增饮食记录
 *   GET  /api/diet/today        今日各餐次记录
 *   GET  /api/diet/history      历史记录
 *   GET  /api/diet/weekly       近 7 天逐日汇总
 *   GET  /api/diet/nutrition    营养汇总
 *   GET  /api/diet/stats        饮食统计
 *   GET  /api/diet/preferences  读取饮食偏好
 *   POST /api/diet/preferences  保存饮食偏好
 *   GET  /api/diet/advice       饮食建议（纯规则）
 */
@Slf4j
@RestController
@RequestMapping("/diet")
@RequiredArgsConstructor
public class DietController {

    private final DietService dietService;
    private final AuthService authService;

    /** 从 Authorization: Bearer xxx 里取 token */
    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    /**
     * 新增饮食记录
     *
     * 请求体：{ "mealType": "breakfast", "foods": ["鸡蛋","牛奶"], "calories": 320 }
     */
    @PostMapping("/record")
    public ApiResponse<Map<String, Object>> addRecord(HttpServletRequest req,
                                                      @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));
        Map<String, Object> data = dietService.addRecord(targetId(user, body), body);
        return ApiResponse.ok("记录成功", data);
    }

    /** 今日各餐次记录 */
    @GetMapping("/today")
    public ApiResponse<Map<String, Object>> today(HttpServletRequest req,
                                                  @RequestParam(required = false) String userId) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(dietService.today(targetId(user, userId)));
    }

    /** 历史记录（默认最近 7 天，支持分页） */
    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> history(HttpServletRequest req,
                                                    @RequestParam(required = false) String userId,
                                                    @RequestParam(defaultValue = "7") int days,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "50") int pageSize) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(dietService.history(targetId(user, userId), days, page, pageSize));
    }

    /** 近 7 天逐日汇总 */
    @GetMapping("/weekly")
    public ApiResponse<Map<String, Object>> weekly(HttpServletRequest req,
                                                   @RequestParam(required = false) String userId) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(dietService.weekly(targetId(user, userId)));
    }

    /** 营养汇总（今日 + 区间，含三大营养素供能比） */
    @GetMapping("/nutrition")
    public ApiResponse<Map<String, Object>> nutrition(HttpServletRequest req,
                                                      @RequestParam(required = false) String userId,
                                                      @RequestParam(defaultValue = "7") int days) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(dietService.nutrition(targetId(user, userId), days));
    }

    /** 饮食统计 */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(HttpServletRequest req,
                                                  @RequestParam(required = false) String userId,
                                                  @RequestParam(defaultValue = "7") int days) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(dietService.stats(targetId(user, userId), days));
    }

    /** 读取饮食偏好 */
    @GetMapping("/preferences")
    public ApiResponse<Map<String, Object>> getPreferences(HttpServletRequest req,
                                                           @RequestParam(required = false) String userId) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(dietService.getPreferences(targetId(user, userId)));
    }

    /**
     * 保存饮食偏好
     *
     * 请求体：{ "preferences": { "lowSalt": true, "avoidFoods": ["海鲜"] } }
     * 也兼容直接把偏好字段平铺在 body 上。
     */
    @PostMapping("/preferences")
    public ApiResponse<Map<String, Object>> setPreferences(HttpServletRequest req,
                                                           @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }

        // 优先取 preferences 字段，否则把整个 body（去掉 userId）当作偏好
        Object prefs = body.get("preferences");
        if (prefs == null) {
            Map<String, Object> flat = new java.util.LinkedHashMap<>(body);
            flat.remove("userId");
            prefs = flat;
        }

        Map<String, Object> data = dietService.setPreferences(targetId(user, body), prefs);
        return ApiResponse.ok("设置成功", data);
    }

    /** 饮食建议（纯规则，不调用 AI） */
    @GetMapping("/advice")
    public ApiResponse<Map<String, Object>> advice(HttpServletRequest req,
                                                   @RequestParam(required = false) String userId) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(dietService.advice(targetId(user, userId)));
    }

    // ========================================================================
    // 工具
    // ========================================================================

    /** 未传 userId 时默认操作自己 */
    private String targetId(User user, Map<String, Object> body) {
        if (body == null) return user.getId();
        return targetId(user, body.get("userId") == null ? null : String.valueOf(body.get("userId")));
    }

    private String targetId(User user, String userId) {
        return (userId == null || userId.isBlank()) ? user.getId() : userId.trim();
    }
}
