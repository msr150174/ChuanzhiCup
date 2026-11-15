package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.entity.User;
import com.guardian.service.AuthService;
import com.guardian.service.WeChatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证接口
 *
 * 路由（context-path 已带 /api）：
 *   POST /api/auth/send-code     发送验证码
 *   POST /api/auth/login         登录 / 注册
 *   POST /api/auth/logout        登出
 *   GET  /api/auth/user          当前用户
 *   POST /api/auth/user/update   更新资料
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final WeChatService weChatService;

    /** 从 Authorization: Bearer xxx 里取 token */
    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    /** 发送验证码 */
    @PostMapping("/send-code")
    public ApiResponse<Map<String, Object>> sendCode(@RequestBody Map<String, String> body) {
        String phone = body == null ? null : body.get("phone");
        if (phone == null || phone.isBlank()) {
            throw BizException.badRequest("请输入手机号");
        }
        return ApiResponse.ok(authService.sendCode(phone.trim()));
    }

    /** 登录（用户不存在时自动注册） */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }
        String phone = body.get("phone");
        String code = body.get("code");
        return ApiResponse.ok(authService.login(phone == null ? null : phone.trim(), code));
    }

    /** 登出 */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest req) {
        authService.logout(token(req));
        return ApiResponse.ok();
    }

    /** 当前登录用户 */
    @GetMapping("/user")
    public ApiResponse<Map<String, Object>> currentUser(HttpServletRequest req) {
        User u = authService.requireUser(token(req));
        return ApiResponse.ok(authService.toUserView(u));
    }

    /** 更新资料 */
    @PostMapping("/user/update")
    public ApiResponse<Map<String, Object>> updateUser(HttpServletRequest req,
                                                       @RequestBody Map<String, Object> body) {
        User u = authService.requireUser(token(req));
        User updated = authService.updateProfile(u.getId(), body);
        return ApiResponse.ok("资料已更新", authService.toUserView(updated));
    }

    /**
     * 微信登录
     *
     * 请求体：{ "code": "wx.login() 拿到的 code", "phone": "可选", "name": "可选" }
     *
     * 流程：小程序 wx.login() → code → 后端换 openid → 建号或登录 → 签发 token
     *
     * ★ AppID / AppSecret 沿用「智跑校园」那一套（环境变量 WX_APP_ID / WX_APP_SECRET）。
     *   未配置时返回 available=false，让前端提示改用手机号登录，而不是报错。
     */
    @PostMapping("/wechat-login")
    public ApiResponse<Map<String, Object>> wechatLogin(@RequestBody(required = false) Map<String, String> body) {
        Map<String, Object> r = new HashMap<>();

        if (!weChatService.isConfigured()) {
            r.put("available", false);
            r.put("reason", "未配置 WX_APP_ID / WX_APP_SECRET");
            return ApiResponse.ok("微信登录暂未开放，请使用手机号登录", r);
        }

        String code = body == null ? null : body.get("code");
        if (code == null || code.isBlank()) {
            r.put("available", true);
            r.put("reason", "缺少 code 参数");
            throw BizException.badRequest("请先调用 wx.login() 获取 code");
        }

        String phone = body.get("phone");
        String name = body.get("name");

        Map<String, Object> result = weChatService.login(code, phone, name);
        result.put("available", true);

        // 补上用户信息，和手机号登录返回结构一致
        String userId = authService.userIdByToken(String.valueOf(result.get("token")));
        if (userId != null) {
            var u = authService.requireUser(String.valueOf(result.get("token")));
            result.put("user", authService.toUserView(u));
        }
        return ApiResponse.ok("登录成功", result);
    }

    /** 校验邀请码（家属绑定用） */
    @GetMapping("/verify-invite")
    public ApiResponse<Map<String, Object>> verifyInvite(@RequestParam String code) {
        // 具体逻辑在 FamilyService，这里只做格式校验
        if (code == null || code.trim().length() != 8) {
            throw BizException.badRequest("邀请码格式不正确（8 位）");
        }
        Map<String, Object> r = new HashMap<>();
        r.put("code", code.trim().toUpperCase());
        r.put("valid", true);
        return ApiResponse.ok(r);
    }

    /** 生成邀请码 */
    @PostMapping("/generate-invite")
    public ApiResponse<Map<String, Object>> generateInvite(HttpServletRequest req) {
        User u = authService.requireUser(token(req));

        Map<String, Object> r = new HashMap<>();
        r.put("inviteCode", CommonUtil.inviteCode());
        r.put("elderId", u.getId());
        r.put("expireHours", 24);
        return ApiResponse.ok(r);
    }
}
