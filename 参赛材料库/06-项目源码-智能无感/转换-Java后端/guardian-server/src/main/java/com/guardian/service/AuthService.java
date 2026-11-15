package com.guardian.service;

import cn.hutool.core.util.StrUtil;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.entity.User;
import com.guardian.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务
 *
 * 登录方式：手机号 + 短信验证码
 *
 * 开发模式下验证码不真发短信，而是打印到日志。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final StringRedisTemplate redis;

    @Value("${guardian.sms.dev-mode:true}")
    private boolean smsDevMode;
    @Value("${guardian.sms.expire-seconds:300}")
    private long codeExpire;
    @Value("${guardian.sms.interval-seconds:60}")
    private long sendInterval;

    private static final String CODE_KEY = "guardian:sms:code:";
    private static final String LIMIT_KEY = "guardian:sms:limit:";
    private static final String TOKEN_KEY = "guardian:token:";

    // ========================================================================
    // 验证码
    // ========================================================================

    /**
     * 发送验证码
     *
     * 有 60 秒频率限制，防止被刷。
     */
    public Map<String, Object> sendCode(String phone) {
        if (!CommonUtil.isPhone(phone)) {
            throw BizException.badRequest("手机号格式不正确");
        }

        // 频率限制
        String limitKey = LIMIT_KEY + phone;
        Boolean canSend = redis.opsForValue().setIfAbsent(limitKey, "1", sendInterval, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(canSend)) {
            Long ttl = redis.getExpire(limitKey, TimeUnit.SECONDS);
            throw BizException.badRequest("请 " + (ttl == null ? sendInterval : ttl) + " 秒后再试");
        }

        String code = CommonUtil.smsCode();
        redis.opsForValue().set(CODE_KEY + phone, code, codeExpire, TimeUnit.SECONDS);

        Map<String, Object> result = new HashMap<>();
        result.put("sent", true);
        result.put("devMode", smsDevMode);

        if (smsDevMode) {
            // ★ 开发模式：验证码只打日志，不真发短信。
            //   用醒目的方框包起来，方便在滚动的控制台里一眼找到。
            log.warn("""

                    ╔══════════════════════════════════════════════════════════════╗
                    ║  【开发模式】短信未真实发送，请用下面的验证码登录            ║
                    ╠══════════════════════════════════════════════════════════════╣
                    ║   手机号：{}                                         
                    ║   验证码：{}                                                 
                    ║   有效期：{} 秒（超时请重新获取）                            
                    ╚══════════════════════════════════════════════════════════════╝
                    """, phone, code, codeExpire);
            result.put("devCode", code);
        } else {
            // 生产环境：调用短信服务商
            // sendRealSms(phone, code);
            log.info("短信已发送至 {}", CommonUtil.maskPhone(phone));
        }

        return result;
    }

    // ========================================================================
    // 登录 / 注册
    // ========================================================================

    /**
     * 手机号 + 验证码登录
     *
     * 用户不存在时自动注册（首次登录即注册）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> login(String phone, String code) {
        if (!CommonUtil.isPhone(phone)) {
            throw BizException.badRequest("手机号格式不正确");
        }
        if (StrUtil.isBlank(code)) {
            throw BizException.badRequest("请输入验证码");
        }

        // 校验验证码
        String cacheKey = CODE_KEY + phone;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached == null) {
            throw BizException.badRequest("验证码已过期，请重新获取");
        }
        if (!cached.equals(code.trim())) {
            throw BizException.badRequest("验证码错误");
        }
        redis.delete(cacheKey);   // 用完即失效，防止重放

        // 查找或创建用户
        User user = userMapper.selectByPhone(phone);
        boolean isNew = false;
        if (user == null) {
            user = createUser(phone);
            isNew = true;
            log.info("新用户注册: {}", CommonUtil.maskPhone(phone));
        }

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw BizException.forbidden("账号已被停用，请联系管理员");
        }

        return buildLoginResult(user, isNew);
    }

    /** 首次登录自动建号 */
    private User createUser(String phone) {
        User u = new User();
        u.setId(CommonUtil.uuid());
        u.setPhone(phone);
        // 昵称默认用脱敏手机号，用户后续可改
        u.setName("用户" + phone.substring(7));
        u.setRole("elder");
        u.setIsActive(true);
        userMapper.insert(u);
        return u;
    }

    /** 组装登录返回：token + 用户信息 */
    private Map<String, Object> buildLoginResult(User user, boolean isNew) {
        String token = CommonUtil.uuid().replace("-", "") + CommonUtil.uuid().replace("-", "");

        // token 存 Redis，7 天有效
        redis.opsForValue().set(TOKEN_KEY + token, user.getId(), 7, TimeUnit.DAYS);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("isNewUser", isNew);
        result.put("user", toUserView(user));
        return result;
    }

    // ========================================================================
    // Token
    // ========================================================================

    /** 根据 token 取 userId，无效返回 null */
    public String userIdByToken(String token) {
        if (StrUtil.isBlank(token)) return null;
        return redis.opsForValue().get(TOKEN_KEY + token);
    }

    /** 校验 token 并返回用户，无效则抛 401 */
    public User requireUser(String token) {
        String userId = userIdByToken(token);
        if (userId == null) {
            throw BizException.unauthorized("请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.unauthorized("用户不存在");
        }
        return user;
    }

    /** 登出 */
    public void logout(String token) {
        if (StrUtil.isNotBlank(token)) {
            redis.delete(TOKEN_KEY + token);
        }
    }

    // ========================================================================
    // 用户信息
    // ========================================================================

    /** 更新用户资料 */
    @Transactional(rollbackFor = Exception.class)
    public User updateProfile(String userId, Map<String, Object> body) {
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw BizException.notFound("用户不存在");
        }

        if (body.containsKey("name")) u.setName((String) body.get("name"));
        if (body.containsKey("avatar")) u.setAvatar((String) body.get("avatar"));
        if (body.containsKey("gender")) u.setGender((String) body.get("gender"));
        if (body.containsKey("address")) u.setAddress((String) body.get("address"));
        if (body.containsKey("emergencyPhone")) u.setEmergencyPhone((String) body.get("emergencyPhone"));
        if (body.containsKey("birthDate") && body.get("birthDate") != null) {
            u.setBirthDate(java.time.LocalDate.parse(String.valueOf(body.get("birthDate"))));
        }

        userMapper.update(u);
        return u;
    }

    /** 转成前端视图（手机号脱敏） */
    public Map<String, Object> toUserView(User u) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", u.getId());
        m.put("phone", CommonUtil.maskPhone(u.getPhone()));
        m.put("name", u.getName());
        m.put("avatar", u.getAvatar());
        m.put("role", u.getRole());
        m.put("gender", u.getGender());
        m.put("birthDate", u.getBirthDate() == null ? null : u.getBirthDate().toString());
        m.put("address", u.getAddress());
        m.put("emergencyPhone", CommonUtil.maskPhone(u.getEmergencyPhone()));
        m.put("isActive", u.getIsActive());
        // 前端据此决定是否显示「后台管理」入口
        m.put("isAdmin", Boolean.TRUE.equals(u.getIsAdmin()));
        m.put("createTime", u.getCreatedAt() == null ? null : u.getCreatedAt().toString());
        return m;
    }

    /**
     * 校验管理员身份，非管理员抛 403
     *
     * 所有 /admin/* 接口都先过这一关。
     */
    public User requireAdmin(String token) {
        User u = requireUser(token);
        if (!Boolean.TRUE.equals(u.getIsAdmin())) {
            throw BizException.forbidden("需要管理员权限");
        }
        return u;
    }
}
