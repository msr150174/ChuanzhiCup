package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.entity.User;
import com.guardian.service.AuthService;
import com.guardian.service.MedicineService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用药接口
 *
 * 路由（context-path 已带 /api）：
 *   GET    /api/medicine/reminders        用药提醒列表
 *   POST   /api/medicine/reminder         新增用药提醒
 *   POST   /api/medicine/reminder/update  更新用药提醒
 *   DELETE /api/medicine/reminder/:id     删除用药提醒
 *   POST   /api/medicine/record           记录服药
 *   GET    /api/medicine/today            今日服药情况（按时段）
 *   GET    /api/medicine/history          服药历史
 *   GET    /api/medicine/stats            服药统计 / 依从率
 */
@Slf4j
@RestController
@RequestMapping("/medicine")
@RequiredArgsConstructor
public class MedicineController {

    private final MedicineService medicineService;
    private final AuthService authService;

    /** 从 Authorization: Bearer xxx 里取 token */
    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    /** 用药提醒列表（isActive 为 null 时返回全部，含已停用） */
    @GetMapping("/reminders")
    public ApiResponse<Map<String, Object>> reminders(HttpServletRequest req,
                                                      @RequestParam(required = false) String userId,
                                                      @RequestParam(required = false) Boolean isActive) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(medicineService.getReminders(targetId(user, userId), isActive));
    }

    /**
     * 新增用药提醒
     *
     * 请求体：{ "medicineName": "降压药", "dosage": "1片", "frequency": "twice",
     *          "times": ["08:00","20:00"] }
     */
    @PostMapping("/reminder")
    public ApiResponse<Map<String, Object>> addReminder(HttpServletRequest req,
                                                        @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));
        Map<String, Object> data = medicineService.addReminder(targetId(user, body), body);
        return ApiResponse.ok("添加成功", data);
    }

    /**
     * 更新用药提醒
     *
     * 请求体：{ "id": "...", "medicineName": "...", "isActive": false }
     */
    @PostMapping("/reminder/update")
    public ApiResponse<Map<String, Object>> updateReminder(HttpServletRequest req,
                                                           @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));
        Map<String, Object> data = medicineService.updateReminder(targetId(user, body), body);
        return ApiResponse.ok("更新成功", data);
    }

    /** 删除用药提醒（软删除） */
    @DeleteMapping("/reminder/{id}")
    public ApiResponse<Map<String, Object>> deleteReminder(HttpServletRequest req,
                                                           @PathVariable String id,
                                                           @RequestParam(required = false) String userId) {
        User user = authService.requireUser(token(req));
        Map<String, Object> data = medicineService.deleteReminder(targetId(user, userId), id);
        return ApiResponse.ok("删除成功", data);
    }

    /**
     * 记录服药
     *
     * 请求体：{ "reminderId": "...", "status": "taken", "time": "08:05" }
     * 兼容旧写法 { "taken": true }
     */
    @PostMapping("/record")
    public ApiResponse<Map<String, Object>> record(HttpServletRequest req,
                                                   @RequestBody Map<String, Object> body) {
        User user = authService.requireUser(token(req));
        Map<String, Object> data = medicineService.recordMedicine(targetId(user, body), body);
        return ApiResponse.ok("记录成功", data);
    }

    /** 今日服药情况（晨起 / 午后 / 睡前三个时段） */
    @GetMapping("/today")
    public ApiResponse<Map<String, Object>> today(HttpServletRequest req,
                                                  @RequestParam(required = false) String userId) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(medicineService.today(targetId(user, userId)));
    }

    /** 服药历史（默认最近 7 天） */
    @GetMapping("/history")
    public ApiResponse<Map<String, Object>> history(HttpServletRequest req,
                                                    @RequestParam(required = false) String userId,
                                                    @RequestParam(defaultValue = "7") int days,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "50") int pageSize) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(medicineService.history(targetId(user, userId), days, page, pageSize));
    }

    /** 服药统计 / 依从率 */
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(HttpServletRequest req,
                                                  @RequestParam(required = false) String userId,
                                                  @RequestParam(defaultValue = "30") int days) {
        User user = authService.requireUser(token(req));
        return ApiResponse.ok(medicineService.stats(targetId(user, userId), days));
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
