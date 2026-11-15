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

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 家庭成员接口
 *
 * ★ 为什么单独开一个 Controller：
 *   小程序端（uni-app）调的是 /api/family/* 这一组路径，
 *   而后端早期实现放在 /api/auth/* 和 /api/guardian/* 下。
 *   与其改前端 36 个页面，不如在这里提供前端期望的路径 —— 前端保持不动。
 *
 * 路由：
 *   GET    /api/family/members     家庭成员列表（已接受）
 *   GET    /api/family/pending     待处理邀请
 *   GET    /api/family/invite      生成/获取我的邀请码
 *   POST   /api/family/add         添加成员（发邀请）
 *   POST   /api/family/accept      接受邀请
 *   POST   /api/family/reject      拒绝邀请
 *   DELETE /api/family/remove      移除成员
 *   POST   /api/family/permission  设置成员权限
 *
 * 成员权限存在 Redis（key: guardian:family:perm:{bindingId}），
 * 因为权限是低频小数据，不值得为它加表。
 */
@Slf4j
@RestController
@RequestMapping("/family")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyBindingMapper bindingMapper;
    private final UserMapper userMapper;
    private final AuthService authService;
    private final StringRedisTemplate redis;

    private static final String PERM_KEY = "guardian:family:perm:";
    private static final String MY_INVITE_KEY = "guardian:myinvite:";

    /** 合法的权限级别 */
    private static final Set<String> LEVELS = Set.of("full", "limited", "view");

    private String token(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h == null) return null;
        return h.startsWith("Bearer ") ? h.substring(7) : h;
    }

    private User me(HttpServletRequest req) {
        return authService.requireUser(token(req));
    }

    // ========================================================================
    // 成员列表
    // ========================================================================

    /**
     * 家庭成员列表
     *
     * 同时返回两种视角：
     *   asElder  —— 我作为长辈，有哪些家属在照护我
     *   asFamily —— 我作为家属，在照护哪些长辈
     */
    @GetMapping("/members")
    public ApiResponse<Map<String, Object>> members(HttpServletRequest req) {
        User user = me(req);

        List<Map<String, Object>> asElder = bindingMapper.selectMembersWithUser(user.getId());

        List<Map<String, Object>> asFamily = new ArrayList<>();
        for (FamilyBinding b : bindingMapper.selectByFamily(user.getId())) {
            if (!"accepted".equals(b.getStatus())) continue;

            User elder = userMapper.selectById(b.getElderId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bindingId", b.getId());
            m.put("userId", b.getElderId());
            m.put("name", elder == null ? null : elder.getName());
            m.put("phone", elder == null ? null : CommonUtil.maskPhone(elder.getPhone()));
            m.put("avatar", elder == null ? null : elder.getAvatar());
            m.put("role", "elder");
            m.put("status", b.getStatus());
            m.put("createTime", b.getCreatedAt() == null ? null : b.getCreatedAt().toString());
            m.put("permissions", readPermission(b.getId()));
            asFamily.add(m);
        }

        // 给 asElder 补上权限
        for (Map<String, Object> m : asElder) {
            Object bid = m.get("bindingId");
            m.put("role", "family");
            m.put("permissions", readPermission(bid == null ? null : String.valueOf(bid)));
            Object phone = m.get("phone");
            if (phone != null) {
                m.put("phone", CommonUtil.maskPhone(String.valueOf(phone)));
            }
        }

        List<Map<String, Object>> all = new ArrayList<>();
        all.addAll(asElder);
        all.addAll(asFamily);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", all);
        data.put("total", all.size());
        data.put("asElder", asElder);
        data.put("asFamily", asFamily);
        return ApiResponse.ok(data);
    }

    /** 待处理的邀请（别人邀请我 / 我发出的还没被接受） */
    @GetMapping("/pending")
    public ApiResponse<Map<String, Object>> pending(HttpServletRequest req) {
        User user = me(req);

        List<Map<String, Object>> received = new ArrayList<>();
        for (FamilyBinding b : bindingMapper.selectByFamily(user.getId())) {
            if (!"pending".equals(b.getStatus())) continue;

            User elder = userMapper.selectById(b.getElderId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bindingId", b.getId());
            m.put("userId", b.getElderId());
            m.put("name", elder == null ? null : elder.getName());
            m.put("phone", elder == null ? null : CommonUtil.maskPhone(elder.getPhone()));
            m.put("direction", "received");
            m.put("createTime", b.getCreatedAt() == null ? null : b.getCreatedAt().toString());
            received.add(m);
        }

        List<Map<String, Object>> sent = new ArrayList<>();
        for (FamilyBinding b : bindingMapper.selectByElder(user.getId())) {
            if (!"pending".equals(b.getStatus())) continue;

            User fam = userMapper.selectById(b.getFamilyId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bindingId", b.getId());
            m.put("userId", b.getFamilyId());
            m.put("name", fam == null ? null : fam.getName());
            m.put("phone", fam == null ? null : CommonUtil.maskPhone(fam.getPhone()));
            m.put("direction", "sent");
            m.put("createTime", b.getCreatedAt() == null ? null : b.getCreatedAt().toString());
            sent.add(m);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("received", received);
        data.put("sent", sent);
        data.put("list", received);
        data.put("total", received.size() + sent.size());
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // 邀请码
    // ========================================================================

    /**
     * 获取我的邀请码
     *
     * 已有就返回同一个（存在 Redis），没有就生成一个。
     */
    @GetMapping("/invite")
    public ApiResponse<Map<String, Object>> invite(HttpServletRequest req) {
        User user = me(req);

        String code = null;
        try {
            code = redis.opsForValue().get(MY_INVITE_KEY + user.getId());
        } catch (Exception e) {
            log.warn("读取邀请码失败: {}", e.getMessage());
        }

        if (code == null || code.isBlank()) {
            code = CommonUtil.inviteCode();
            try {
                redis.opsForValue().set(MY_INVITE_KEY + user.getId(), code, 7, TimeUnit.DAYS);
            } catch (Exception e) {
                log.warn("写入邀请码失败: {}", e.getMessage());
            }
        }

        // 同时落一条 pending 绑定，方便对方凭码接受
        FamilyBinding exists = bindingMapper.selectByInviteCode(code);
        if (exists == null) {
            FamilyBinding b = new FamilyBinding();
            b.setId(CommonUtil.uuid());
            b.setElderId(user.getId());
            // family_id 是 NOT NULL，用空串占位表示「还没人接」
            b.setFamilyId("");
            b.setInviteCode(code);
            b.setStatus("pending");
            try {
                bindingMapper.insert(b);
            } catch (Exception e) {
                log.warn("写入邀请记录失败: {}", e.getMessage());
            }
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("inviteCode", code);
        data.put("elderId", user.getId());
        data.put("expireDays", 7);
        data.put("tip", "把邀请码发给家属，对方在「成员绑定」页输入即可加入");
        return ApiResponse.ok(data);
    }

    // ========================================================================
    // 增删改
    // ========================================================================

    /**
     * 添加成员
     *
     * 请求体：{ "phone": "138...", "relation": "儿子" }
     *
     * 手机号对方已注册 → 直接建立 accepted 关系
     * 手机号没注册   → 建立 pending，等对方注册后接受
     */
    @PostMapping("/add")
    public ApiResponse<Map<String, Object>> add(HttpServletRequest req,
                                                @RequestBody Map<String, Object> body) {
        User user = me(req);

        String phone = body == null ? null : str(body.get("phone"));
        String relation = body == null ? null : str(body.get("relation"));

        if (!CommonUtil.isPhone(phone)) {
            throw BizException.badRequest("手机号格式不正确");
        }
        if (phone.equals(user.getPhone())) {
            throw BizException.badRequest("不能添加自己");
        }

        // 已有关系就不重复加
        for (FamilyBinding b : bindingMapper.selectByElder(user.getId())) {
            User other = userMapper.selectById(b.getFamilyId());
            if (other != null && phone.equals(other.getPhone())) {
                throw BizException.badRequest("该成员已在你的家庭列表中");
            }
        }

        User target = userMapper.selectByPhone(phone);
        FamilyBinding b = new FamilyBinding();
        b.setId(CommonUtil.uuid());
        b.setElderId(user.getId());
        // ★ family_bindings.family_id 是 NOT NULL，
        //   对方还没注册时不能写 null，用空串占位表示「待注册」，
        //   等对方注册后凭邀请码接受，acceptWithFamily 会把真实 ID 补上。
        b.setFamilyId(target == null ? "" : target.getId());
        b.setInviteCode(CommonUtil.inviteCode());
        b.setStatus(target == null ? "pending" : "accepted");

        bindingMapper.insert(b);
        writePermission(b.getId(), "limited");

        log.info("[家庭] {} 添加成员 {}，状态={}", user.getName(), phone, b.getStatus());

        String message = target == null
                ? "对方还没注册，等他注册后会自动加入"
                : "已添加为家庭成员";

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bindingId", b.getId());
        data.put("name", target == null ? null : target.getName());
        data.put("phone", CommonUtil.maskPhone(phone));
        data.put("relation", relation);
        data.put("status", b.getStatus());
        data.put("needRegister", target == null);
        data.put("message", message);
        return ApiResponse.ok(message, data);
    }

    /** 接受邀请 */
    @PostMapping("/accept")
    public ApiResponse<Map<String, Object>> accept(HttpServletRequest req,
                                                   @RequestBody Map<String, Object> body) {
        User user = me(req);

        String bindingId = body == null ? null : str(body.get("id"));
        if (bindingId == null) bindingId = body == null ? null : str(body.get("bindingId"));
        if (bindingId == null || bindingId.isBlank()) {
            throw BizException.badRequest("缺少绑定 ID");
        }

        FamilyBinding b = findBinding(user, bindingId);
        if ("accepted".equals(b.getStatus())) {
            throw BizException.badRequest("已经接受过了");
        }
        if (user.getId().equals(b.getElderId())) {
            throw BizException.badRequest("这是你自己发出的邀请");
        }

        // 接受时补上 family_id（邀请时对方可能还没注册，family_id 是空的）
        bindingMapper.acceptWithFamily(bindingId, user.getId());

        writePermission(bindingId, "limited");
        log.info("[家庭] {} 接受了邀请 {}", user.getName(), bindingId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bindingId", bindingId);
        data.put("status", "accepted");
        return ApiResponse.ok("已加入家庭", data);
    }

    /** 拒绝邀请 */
    @PostMapping("/reject")
    public ApiResponse<Map<String, Object>> reject(HttpServletRequest req,
                                                   @RequestBody Map<String, Object> body) {
        User user = me(req);

        String bindingId = body == null ? null : str(body.get("id"));
        if (bindingId == null) bindingId = body == null ? null : str(body.get("bindingId"));
        if (bindingId == null || bindingId.isBlank()) {
            throw BizException.badRequest("缺少绑定 ID");
        }

        findBinding(user, bindingId);
        bindingMapper.updateStatus(bindingId, "rejected");
        log.info("[家庭] {} 拒绝了邀请 {}", user.getName(), bindingId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bindingId", bindingId);
        data.put("status", "rejected");
        return ApiResponse.ok("已拒绝", data);
    }

    /** 移除成员 */
    @DeleteMapping("/remove")
    public ApiResponse<Map<String, Object>> remove(HttpServletRequest req,
                                                   @RequestParam(required = false) String id,
                                                   @RequestParam(required = false) String bindingId) {
        User user = me(req);

        String target = id != null ? id : bindingId;
        if (target == null || target.isBlank()) {
            throw BizException.badRequest("缺少绑定 ID");
        }

        findBinding(user, target);
        bindingMapper.deleteById(target);

        try {
            redis.delete(PERM_KEY + target);
        } catch (Exception e) {
            log.warn("清理权限失败: {}", e.getMessage());
        }

        log.info("[家庭] {} 移除了成员 {}", user.getName(), target);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bindingId", target);
        data.put("removed", true);
        return ApiResponse.ok("已移除", data);
    }

    /**
     * 设置成员权限
     *
     * 请求体：{ "id": "...", "level": "full|limited|view" }
     *   full    全部权限（可看作主要监护人）
     *   limited 部分权限（只能查看健康数据）
     *   view    仅查看
     */
    @PostMapping("/permission")
    public ApiResponse<Map<String, Object>> permission(HttpServletRequest req,
                                                       @RequestBody Map<String, Object> body) {
        User user = me(req);

        String bindingId = body == null ? null : str(body.get("id"));
        if (bindingId == null) bindingId = body == null ? null : str(body.get("bindingId"));
        String level = body == null ? null : str(body.get("level"));

        if (bindingId == null || bindingId.isBlank()) {
            throw BizException.badRequest("缺少绑定 ID");
        }
        if (level == null || !LEVELS.contains(level)) {
            throw BizException.badRequest("权限级别不合法（full / limited / view）");
        }

        findBinding(user, bindingId);

        // 设为主要监护人时，其他成员自动降为 limited
        if ("full".equals(level)) {
            for (FamilyBinding b : bindingMapper.selectByElder(user.getId())) {
                if (!b.getId().equals(bindingId)) {
                    writePermission(b.getId(), "limited");
                }
            }
        }

        writePermission(bindingId, level);
        log.info("[家庭] {} 将成员 {} 的权限设为 {}", user.getName(), bindingId, level);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bindingId", bindingId);
        data.put("level", level);
        data.put("permissions", readPermission(bindingId));
        return ApiResponse.ok("权限已更新", data);
    }

    // ========================================================================
    // 权限读写（存 Redis）
    // ========================================================================

    /**
     * 权限结构
     *
     * level 决定基础能力，其余是细粒度开关。
     */
    private Map<String, Object> defaultPermission(String level) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", level);
        m.put("viewHealth", true);
        m.put("viewLocation", !"view".equals(level));
        m.put("receiveAlert", true);
        m.put("handleAlert", !"view".equals(level));
        m.put("editProfile", "full".equals(level));
        m.put("manageMembers", "full".equals(level));
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPermission(String bindingId) {
        if (bindingId == null) {
            return defaultPermission("limited");
        }
        try {
            Object raw = redis.opsForHash().get(PERM_KEY + bindingId, "json");
            if (raw == null) {
                return defaultPermission("limited");
            }
            String json = String.valueOf(raw);
            Map<String, Object> m = new LinkedHashMap<>();
            String s = json.replace("{", "").replace("}", "");
            for (String kv : s.split(",")) {
                String[] p = kv.split(":", 2);
                if (p.length == 2) {
                    String k = p[0].trim().replace("\"", "");
                    String v = p[1].trim().replace("\"", "");
                    m.put(k, "true".equals(v) ? true : "false".equals(v) ? false : v);
                }
            }
            return m.isEmpty() ? defaultPermission("limited") : m;
        } catch (Exception e) {
            return defaultPermission("limited");
        }
    }

    private void writePermission(String bindingId, String level) {
        if (bindingId == null) return;
        Map<String, Object> perm = defaultPermission(level);
        StringBuilder sb = new StringBuilder("{");
        int i = 0;
        for (Map.Entry<String, Object> e : perm.entrySet()) {
            if (i++ > 0) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":\"").append(e.getValue()).append("\"");
        }
        sb.append("}");
        try {
            redis.opsForHash().put(PERM_KEY + bindingId, "json", sb.toString());
            redis.expire(PERM_KEY + bindingId, 365, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("写入权限失败: {}", e.getMessage());
        }
    }

    // ========================================================================
    // 工具
    // ========================================================================

    /** 找到绑定关系并校验当前用户有权操作 */
    private FamilyBinding findBinding(User user, String bindingId) {
        FamilyBinding found = null;
        for (FamilyBinding b : bindingMapper.selectByElder(user.getId())) {
            if (b.getId().equals(bindingId)) {
                found = b;
                break;
            }
        }
        if (found == null) {
            for (FamilyBinding b : bindingMapper.selectByFamily(user.getId())) {
                if (b.getId().equals(bindingId)) {
                    found = b;
                    break;
                }
            }
        }
        if (found == null) {
            throw BizException.notFound("绑定关系不存在或无权操作");
        }
        return found;
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
