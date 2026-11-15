package com.guardian.controller;

import com.guardian.common.ApiResponse;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.entity.FamilyBinding;
import com.guardian.entity.User;
import com.guardian.mapper.FamilyBindingMapper;
import com.guardian.mapper.UserMapper;
import com.guardian.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 认证 · 补充接口
 *
 * 覆盖原 NestJS 项目 auth 模块的其余路由：
 *   POST /api/auth/register        注册
 *   POST /api/auth/verify-code     单独校验验证码
 *   GET  /api/auth/family-members  家属列表
 *   POST /api/auth/bind-family     绑定家属
 *   POST /api/auth/unbind-family   解绑家属
 *
 * ★ register 与 login 等价（首次登录即注册），但保留独立路径以兼容原前端调用。
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthExtraController {

    private final AuthService authService;
    private final FamilyBindingMapper bindingMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redis;

    private static final String CODE_KEY = "guardian:sms:code:";
    private static final String TOKEN_KEY = "guardian:token:";
    private static final String INVITE_KEY = "guardian:invite:";

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    /**
     * 注册
     *
     * 与登录共用逻辑：验证码正确即建号并返回 token。
     * 额外接受 name / role 参数，注册时一并写入资料。
     */
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }

        String phone = str(body.get("phone"));
        String code = str(body.get("code"));
        if (!CommonUtil.isPhone(phone)) {
            throw BizException.badRequest("手机号格式不正确");
        }
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("请输入验证码");
        }

        // 复用登录逻辑（验证码校验 + 自动建号 + 签发 token）
        Map<String, Object> result = authService.login(phone, code);

        // 补充资料
        String name = str(body.get("name"));
        String role = str(body.get("role"));
        if ((name != null && !name.isBlank()) || (role != null && !role.isBlank())) {
            @SuppressWarnings("unchecked")
            Map<String, Object> userView = (Map<String, Object>) result.get("user");
            String userId = str(userView.get("id"));

            Map<String, Object> update = new HashMap<>();
            if (name != null && !name.isBlank()) update.put("name", name);
            if (role != null && !role.isBlank()) update.put("role", role);
            authService.updateProfile(userId, update);

            User fresh = userMapper.selectById(userId);
            if (fresh != null) {
                result.put("user", authService.toUserView(fresh));
            }
        }

        return ApiResponse.ok("注册成功", result);
    }

    /**
     * 单独校验验证码
     *
     * 用于「下一步」式分步注册：先验码，再填资料。
     * 校验通过后**不删除验证码**（后续 register 还要用）。
     */
    @PostMapping("/verify-code")
    public ApiResponse<Map<String, Object>> verifyCode(@RequestBody Map<String, String> body) {
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }

        String phone = body.get("phone");
        String code = body.get("code");
        if (!CommonUtil.isPhone(phone)) {
            throw BizException.badRequest("手机号格式不正确");
        }
        if (code == null || code.isBlank()) {
            throw BizException.badRequest("请输入验证码");
        }

        String cached = redis.opsForValue().get(CODE_KEY + phone);
        if (cached == null) {
            throw BizException.badRequest("验证码已过期，请重新获取");
        }
        if (!cached.equals(code.trim())) {
            throw BizException.badRequest("验证码错误");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", true);
        data.put("phone", CommonUtil.maskPhone(phone));
        data.put("isRegistered", userMapper.selectByPhone(phone) != null);
        return ApiResponse.ok(data);
    }

    /**
     * 家属列表
     *
     * 不传 userId 时返回「与我相关的全部绑定」：
     *   - 我作为长辈，绑定了哪些家属
     *   - 我作为家属，绑定了哪些长辈
     */
    @GetMapping("/family-members")
    public ApiResponse<Map<String, Object>> familyMembers(HttpServletRequest req,
                                                          @RequestParam(required = false) String userId) {
        User me = authService.requireUser(token(req));
        String targetId = (userId == null || userId.isBlank()) ? me.getId() : userId;

        List<Map<String, Object>> list = new ArrayList<>();

        // 我作为长辈 → 查家属
        for (FamilyBinding b : bindingMapper.selectByElder(targetId)) {
            list.add(toMemberView(b, b.getFamilyId(), "family"));
        }

        // 我作为家属 → 查长辈
        for (FamilyBinding b : bindingMapper.selectByFamily(targetId)) {
            list.add(toMemberView(b, b.getElderId(), "elder"));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", targetId);
        data.put("list", list);
        data.put("total", list.size());
        return ApiResponse.ok(data);
    }

    private Map<String, Object> toMemberView(FamilyBinding b, String otherUserId, String relation) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bindingId", b.getId());
        m.put("userId", otherUserId);
        m.put("relation", relation);
        m.put("status", b.getStatus());
        m.put("inviteCode", b.getInviteCode());

        User other = userMapper.selectById(otherUserId);
        if (other != null) {
            m.put("name", other.getName());
            m.put("phone", CommonUtil.maskPhone(other.getPhone()));
            m.put("avatar", other.getAvatar());
            m.put("role", other.getRole());
        }
        m.put("createTime", b.getCreatedAt() == null ? null : b.getCreatedAt().toString());
        return m;
    }

    /**
     * 绑定家属
     *
     * 请求体二选一：
     *   { "inviteCode": "ABCD1234" }   —— 用邀请码
     *   { "familyPhone": "138..." }    —— 用手机号直接邀请
     */
    @PostMapping("/bind-family")
    public ApiResponse<Map<String, Object>> bindFamily(HttpServletRequest req,
                                                       @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }

        String inviteCode = str(body.get("inviteCode"));
        String familyPhone = str(body.get("familyPhone"));

        // ---- 方式一：用邀请码 ----
        if (inviteCode != null && !inviteCode.isBlank()) {
            FamilyBinding binding = bindingMapper.selectByInviteCode(inviteCode.trim().toUpperCase());
            if (binding == null) {
                throw BizException.notFound("邀请码无效或已过期");
            }
            if ("accepted".equals(binding.getStatus())) {
                throw BizException.badRequest("该邀请码已被使用");
            }
            if (binding.getElderId().equals(me.getId())) {
                throw BizException.badRequest("不能绑定自己");
            }
            bindingMapper.updateStatus(binding.getId(), "accepted");

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("bindingId", binding.getId());
            data.put("elderId", binding.getElderId());
            data.put("status", "accepted");
            return ApiResponse.ok("绑定成功", data);
        }

        // ---- 方式二：用手机号（生成邀请码待对方确认）----
        if (CommonUtil.isPhone(familyPhone)) {
            User other = userMapper.selectByPhone(familyPhone);
            if (other == null) {
                throw BizException.notFound("该手机号尚未注册");
            }
            if (other.getId().equals(me.getId())) {
                throw BizException.badRequest("不能绑定自己");
            }

            String code = CommonUtil.inviteCode();
            FamilyBinding b = new FamilyBinding();
            b.setId(CommonUtil.uuid());
            b.setElderId(me.getId());
            b.setFamilyId(other.getId());
            b.setInviteCode(code);
            b.setStatus("pending");
            bindingMapper.insert(b);

            redis.opsForValue().set(INVITE_KEY + code, me.getId(), 24, TimeUnit.HOURS);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("bindingId", b.getId());
            data.put("inviteCode", code);
            data.put("status", "pending");
            data.put("targetName", other.getName());
            return ApiResponse.ok("邀请已发送，等待对方确认", data);
        }

        throw BizException.badRequest("请提供邀请码或家属手机号");
    }

    /** 解绑家属 */
    @PostMapping("/unbind-family")
    public ApiResponse<Map<String, Object>> unbindFamily(HttpServletRequest req,
                                                         @RequestBody Map<String, Object> body) {
        User me = authService.requireUser(token(req));
        if (body == null) {
            throw BizException.badRequest("请求体为空");
        }

        String bindingId = str(body.get("bindingId"));
        String familyId = str(body.get("familyId"));
        String elderId = str(body.get("elderId"));

        String targetId = bindingId;

        // 没给 bindingId 时，按 familyId 或 elderId 反查
        if (targetId == null) {
            if (familyId != null) {
                for (FamilyBinding b : bindingMapper.selectByElder(me.getId())) {
                    if (familyId.equals(b.getFamilyId())) {
                        targetId = b.getId();
                        break;
                    }
                }
            } else if (elderId != null) {
                for (FamilyBinding b : bindingMapper.selectByFamily(me.getId())) {
                    if (elderId.equals(b.getElderId())) {
                        targetId = b.getId();
                        break;
                    }
                }
            }
        }

        if (targetId == null) {
            throw BizException.badRequest("未找到对应的绑定关系");
        }

        bindingMapper.deleteById(targetId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("bindingId", targetId);
        return ApiResponse.ok("已解除绑定", data);
    }

    /** 当前 token 对应的 userId（便于前端调试） */
    @GetMapping("/token-info")
    public ApiResponse<Map<String, Object>> tokenInfo(HttpServletRequest req) {
        String t = token(req);
        String userId = t == null ? null : redis.opsForValue().get(TOKEN_KEY + t);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", userId != null);
        data.put("userId", userId);
        return ApiResponse.ok(data);
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
