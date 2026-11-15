package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.entity.User;
import com.guardian.service.AuthService;
import com.guardian.service.ExerciseService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 运动接口
 *
 * 路由（context-path 已带 /api）：
 *   POST /api/exercise/record    新增运动记录
 *   GET  /api/exercise/today     今日运动
 *   GET  /api/exercise/history   历史记录
 *   GET  /api/exercise/weekly    近 7 天汇总
 *   GET  /api/exercise/monthly   近 30 天汇总
 *   GET  /api/exercise/types     运动类型分布
 *   GET  /api/exercise/stats     运动统计
 *   GET  /api/exercise/goals     读取运动目标
 *   POST /api/exercise/goals     设置运动目标（会话级，未持久化）
 */
@Slf4j
@RestController
@RequestMapping("/exercise")
@RequiredArgsConstructor
public class ExerciseController {

    private final ExerciseService exerciseService;
    private final AuthService authService;

    /** 从 Authorization: Bearer xxx 里取 token */
    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    /**
     * 新增运动记录
     *
     * 请求体：{ "exerciseType": "walk", "duration": 30, "steps": 3000, "calories": 120 }
     */
    @PostMapping("/record")
    public ApiResponse<Map<String, Object>> addRecord(HttpServletRequest req,
                                                      @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));
        Map<String, Object> data = exerciseService.addRecord(targetId(user, body), body);
        return ApiResponse.ok("记录成功", data);
    }

    /** 今日运动 */
    @GetMapping("/today")
    public ApiResponse<Map<String, Object>> today(HttpServletRequest req,
                                                  @RequestParam(required = false) String userId) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(exerciseService.today(targetId(user, userId)));
    }

    /** 历史记录（默认最近 7 天，支持分页） */
    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> history(HttpServletRequest req,
                                                    @RequestParam(required = false) String userId,
                                                    @RequestParam(defaultValue = "7") int days,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "50") int pageSize) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(exerciseService.history(targetId(user, userId), days, page, pageSize));
    }

    /** 近 7 天逐日汇总 */
    @GetMapping("/weekly")
    public ApiResponse<Map<String, Object>> weekly(HttpServletRequest req,
                                                   @RequestParam(required = false) String userId) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(exerciseService.weekly(targetId(user, userId)));
    }

    /** 近 30 天汇总 */
    @GetMapping("/monthly")
    public ApiResponse<Map<String, Object>> monthly(HttpServletRequest req,
                                                    @RequestParam(required = false) String userId) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(exerciseService.monthly(targetId(user, userId)));
    }

    /** 运动类型分布（默认近 30 天） */
    @GetMapping("/types")
    public ApiResponse<Map<String, Object>> types(HttpServletRequest req,
                                                  @RequestParam(required = false) String userId,
                                                  @RequestParam(defaultValue = "30") int days) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(exerciseService.typeStats(targetId(user, userId), days));
    }

    /** 运动统计 */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(HttpServletRequest req,
                                                  @RequestParam(required = false) String userId,
                                                  @RequestParam(defaultValue = "7") int days) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(exerciseService.stats(targetId(user, userId), days));
    }

    /** 读取运动目标（含今日进度） */
    @GetMapping("/goals")
    public ApiResponse<Map<String, Object>> getGoals(HttpServletRequest req,
                                                     @RequestParam(required = false) String userId) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(exerciseService.getGoals(targetId(user, userId)));
    }

    /**
     * 设置运动目标
     *
     * 请求体：{ "dailySteps": 8000, "dailyDuration": 30, "dailyCalories": 300 }
     * 也兼容 { "goals": { ... } } 嵌套写法。
     *
     * ★ 数据库没有 exercise_goals 表，仅在会话内暂存，返回中会明确说明未持久化。
     */
    @PostMapping("/goals")
    public ApiResponse<Map<String, Object>> setGoals(HttpServletRequest req,
                                                     @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }

        Map<String, Object> data = exerciseService.setGoals(targetId(user, body), body);
        return ApiResponse.ok("目标设置已保存（当前为会话级，后续可持久化）", data);
    }

    // ========================================================================
    // 工具
    // ========================================================================

    private String targetId(User user, Map<String, Object> body) {
        if (body == null) return user.getId();
        Object uid = body.get("userId");
        return targetId(user, uid == null ? null : String.valueOf(uid));
    }

    private String targetId(User user, String userId) {
        return (userId == null || userId.isBlank()) ? user.getId() : userId.trim();
    }
}
