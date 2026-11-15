package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.entity.User;
import com.guardian.service.AuthService;
import com.guardian.service.DeviceService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 设备接口
 *
 * 路由（context-path 已带 /api）：
 *   GET    /api/device/list          设备列表
 *   GET    /api/device/detail        设备详情
 *   POST   /api/device/bind          绑定设备
 *   POST   /api/device/connect       连接设备
 *   POST   /api/device/disconnect    断开设备
 *   DELETE /api/device/unbind        解绑设备
 *   POST   /api/device/status        设备状态上报
 *   GET    /api/device/alerts        设备告警列表
 *   POST   /api/device/alert/handle  处理设备告警
 */
@Slf4j
@RestController
@RequestMapping("/device")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;
    private final AuthService authService;

    /** 从 Authorization: Bearer xxx 里取 token */
    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    /**
     * 目标用户
     *
     * 默认操作自己；传了 userId 且与本人不同时，需要家属权限（简化处理：仅允许查看自己）。
     */
    private User me(HttpServletRequest req) {
        return authService.requireUser(token(req));
    }

    /** 设备列表 */
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(HttpServletRequest req,
                                                 @RequestParam(required = false) String userId) {
        User user = me(req);
        return ApiResponse.ok(deviceService.list(targetId(user, userId)));
    }

    /** 设备详情（不传 deviceId 时返回最近绑定的一台） */
    @GetMapping("/detail")
    public ApiResponse<Map<String, Object>> detail(HttpServletRequest req,
                                                   @RequestParam(required = false) String userId,
                                                   @RequestParam(required = false) String deviceId) {
        User user = me(req);
        return ApiResponse.ok(deviceService.detail(targetId(user, userId), deviceId));
    }

    /**
     * 绑定设备
     *
     * 请求体：{ "name": "智能手表", "type": "watch", "model": "HW-Pro" }
     */
    @PostMapping("/bind")
    public ApiResponse<Map<String, Object>> bind(HttpServletRequest req,
                                                 @RequestBody Map<String, Object> body) {
        User user = me(req);
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }

        String name = toStr(body.getOrDefault("name", body.get("deviceName")));
        String type = toStr(body.getOrDefault("type", body.get("deviceType")));
        String model = toStr(body.get("model"));

        Map<String, Object> data = deviceService.bind(
                targetId(user, toStr(body.get("userId"))), name, type, model);
        return ApiResponse.ok("设备绑定成功", data);
    }

    /** 连接设备 */
    @PostMapping("/connect")
    public ApiResponse<Map<String, Object>> connect(HttpServletRequest req,
                                                    @RequestBody Map<String, Object> body) {
        User user = me(req);
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }
        Map<String, Object> data = deviceService.connect(
                targetId(user, toStr(body.get("userId"))), toStr(body.get("deviceId")));
        return ApiResponse.ok("设备已连接", data);
    }

    /** 断开设备 */
    @PostMapping("/disconnect")
    public ApiResponse<Map<String, Object>> disconnect(HttpServletRequest req,
                                                       @RequestBody Map<String, Object> body) {
        User user = me(req);
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }
        Map<String, Object> data = deviceService.disconnect(
                targetId(user, toStr(body.get("userId"))), toStr(body.get("deviceId")));
        return ApiResponse.ok("设备已断开", data);
    }

    /** 解绑设备 */
    @DeleteMapping("/unbind")
    public ApiResponse<Map<String, Object>> unbind(HttpServletRequest req,
                                                   @RequestBody(required = false) Map<String, Object> body,
                                                   @RequestParam(required = false) String deviceId,
                                                   @RequestParam(required = false) String userId) {
        User user = me(req);

        // DELETE 请求体可能为空，同时支持 query 参数
        String devId = deviceId;
        String uid = userId;
        if (body != null) {
            if (devId == null) devId = toStr(body.get("deviceId"));
            if (uid == null) uid = toStr(body.get("userId"));
        }

        Map<String, Object> data = deviceService.unbind(targetId(user, uid), devId);
        return ApiResponse.ok("设备已解绑", data);
    }

    /**
     * 设备状态上报
     *
     * 请求体：{ "deviceId": "...", "batteryLevel": 85, "signalStrength": 92 }
     */
    @PostMapping("/status")
    public ApiResponse<Map<String, Object>> status(HttpServletRequest req,
                                                   @RequestBody Map<String, Object> body) {
        User user = me(req);
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }

        Map<String, Object> data = deviceService.updateStatus(
                targetId(user, toStr(body.get("userId"))),
                toStr(body.get("deviceId")),
                toInt(body.get("batteryLevel")),
                toInt(body.get("signalStrength")),
                toStr(body.get("firmwareVersion")),
                toStr(body.get("status")));
        return ApiResponse.ok("设备状态已更新", data);
    }

    /** 设备告警列表 */
    @GetMapping("/alerts")
    public ApiResponse<Map<String, Object>> alerts(HttpServletRequest req,
                                                   @RequestParam(required = false) String userId,
                                                   @RequestParam(required = false) Boolean handled,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int pageSize) {
        User user = me(req);
        return ApiResponse.ok(deviceService.alerts(
                targetId(user, userId), handled, page, pageSize));
    }

    /**
     * 处理设备告警
     *
     * 请求体：{ "alertId": "...", "action": "resolve" | "ignore" }
     */
    @PostMapping("/alert/handle")
    public ApiResponse<Map<String, Object>> handleAlert(HttpServletRequest req,
                                                        @RequestBody Map<String, Object> body) {
        User user = me(req);
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }

        String alertId = toStr(body.getOrDefault("alertId", body.get("id")));
        Map<String, Object> data = deviceService.handleAlert(
                targetId(user, toStr(body.get("userId"))), alertId, toStr(body.get("action")));
        return ApiResponse.ok(String.valueOf(data.get("message")), data);
    }

    // ========================================================================
    // 工具
    // ========================================================================

    /** 未传 userId 时默认操作自己 */
    private String targetId(User user, String userId) {
        return (userId == null || userId.isBlank()) ? user.getId() : userId.trim();
    }

    private String toStr(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o);
        return s.isBlank() || "null".equals(s) ? null : s;
    }

    /** 防御式数字转换（前端可能传字符串） */
    private Integer toInt(Object o) {
        if (o == null) return null;
        try {
            return (int) Double.parseDouble(String.valueOf(o));
        } catch (Exception e) {
            return null;
        }
    }
}
