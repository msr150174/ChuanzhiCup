package com.guardian.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.guardian.common.BizException;
import com.guardian.common.CommonUtil;
import com.guardian.entity.User;
import com.guardian.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 微信登录服务
 *
 * 流程：
 *   ① 小程序调 wx.login() 拿 code
 *   ② 后端用 code + AppID + AppSecret 调 code2Session 换 openid
 *   ③ 用 openid 建号或登录，签发自己的 token
 *
 * ★ AppID / AppSecret 从环境变量读，与「智跑校园」共用同一套。
 * ★ 未配置时返回 available=false 而不是假装成功 —— 前端据此提示用户改用手机号登录。
 *
 * 微信接口文档：
 *   https://developers.weixin.qq.com/miniprogram/dev/api-backend/open-api/login/auth.code2Session.html
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatService {

    private final UserMapper userMapper;
    private final StringRedisTemplate redis;

    @Value("${guardian.wechat.app-id:}")
    private String appId;

    @Value("${guardian.wechat.app-secret:}")
    private String appSecret;

    private static final String CODE2SESSION = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String OPENID_KEY = "guardian:wx:openid:";
    private static final String TOKEN_KEY = "guardian:token:";

    /** 是否已配置（供健康检查） */
    public boolean isConfigured() {
        return StrUtil.isNotBlank(appId) && StrUtil.isNotBlank(appSecret);
    }

    /**
     * code2Session 换 openid
     *
     * @param code wx.login() 返回的临时凭证
     * @return { openid, sessionKey, unionId }
     */
    public Map<String, String> code2Session(String code) {
        if (!isConfigured()) {
            throw BizException.badRequest("微信登录未配置 AppID / AppSecret");
        }
        if (StrUtil.isBlank(code)) {
            throw BizException.badRequest("缺少 code 参数");
        }

        String url = CODE2SESSION
                + "?appid=" + appId
                + "&secret=" + appSecret
                + "&js_code=" + code
                + "&grant_type=authorization_code";

        try {
            String body = HttpUtil.get(url, 8000);
            JSONObject json = JSONUtil.parseObj(body);

            // 微信用 errcode 表示失败，成功时没有这个字段
            Integer errcode = json.getInt("errcode");
            if (errcode != null && errcode != 0) {
                String errmsg = json.getStr("errmsg", "未知错误");
                log.warn("[微信] code2Session 失败: errcode={} errmsg={}", errcode, errmsg);

                // 常见错误给出可读提示
                String hint = switch (errcode) {
                    case 40029 -> "code 无效或已过期";
                    case 45011 -> "请求过于频繁，请稍后再试";
                    case 40013 -> "AppID 无效";
                    case 40125 -> "AppSecret 无效";
                    default -> errmsg;
                };
                throw BizException.badRequest("微信登录失败：" + hint);
            }

            String openid = json.getStr("openid");
            if (StrUtil.isBlank(openid)) {
                throw BizException.serverError("微信未返回 openid");
            }

            Map<String, String> out = new LinkedHashMap<>();
            out.put("openid", openid);
            out.put("sessionKey", json.getStr("session_key", ""));
            out.put("unionId", json.getStr("unionid", ""));
            return out;

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[微信] code2Session 异常: {}", e.getMessage());
            throw BizException.serverError("微信服务暂时不可用");
        }
    }

    /**
     * 微信登录（自动建号）
     *
     * 同一个 openid 第二次登录就是登录，第一次是注册。
     */
    public Map<String, Object> login(String code, String phone, String name) {
        Map<String, String> session = code2Session(code);
        String openid = session.get("openid");

        // 先看这个 openid 有没有绑过账号
        String userId = null;
        try {
            userId = redis.opsForValue().get(OPENID_KEY + openid);
        } catch (Exception e) {
            log.warn("读取 openid 映射失败: {}", e.getMessage());
        }

        User user = userId == null ? null : userMapper.selectById(userId);
        boolean isNew = false;

        if (user == null) {
            // 没绑过 → 建号
            // 有手机号就用手机号，没有就生成一个占位（后续引导绑定）
            String finalPhone = StrUtil.isNotBlank(phone) && CommonUtil.isPhone(phone)
                    ? phone
                    : generatePlaceholderPhone(openid);

            // 该手机号可能已经注册过（用户先用手机号登录，再用微信登录）
            user = userMapper.selectByPhone(finalPhone);

            if (user == null) {
                user = new User();
                user.setId(CommonUtil.uuid());
                user.setPhone(finalPhone);
                user.setName(StrUtil.isNotBlank(name) ? name : "微信用户");
                user.setRole("elder");
                user.setIsActive(true);
                user.setIsAdmin(false);
                userMapper.insert(user);
                isNew = true;
                log.info("[微信] 新用户注册 openid={} phone={}", StrUtil.maxLength(openid, 12), finalPhone);
            }

            // 建立 openid → userId 映射
            try {
                redis.opsForValue().set(OPENID_KEY + openid, user.getId(), 365, TimeUnit.DAYS);
            } catch (Exception e) {
                log.warn("写入 openid 映射失败: {}", e.getMessage());
            }
        }

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw BizException.forbidden("账号已被禁用，请联系管理员");
        }

        // 签发我们自己的 token
        String token = CommonUtil.uuid() + CommonUtil.uuid().substring(0, 16);
        try {
            redis.opsForValue().set(TOKEN_KEY + token, user.getId(), 7, TimeUnit.DAYS);
        } catch (Exception e) {
            throw BizException.serverError("登录态保存失败");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", token);
        out.put("isNewUser", isNew);
        out.put("openid", StrUtil.maxLength(openid, 8) + "****");
        out.put("expireDays", 7);
        return out;
    }

    /**
     * 给没有手机号的微信用户生成占位手机号
     *
     * ★ 用 openid 哈希保证同一用户每次得到相同值，避免重复建号。
     *   前缀用 000 段（不是有效号段），后续引导用户绑定真实手机号。
     */
    private String generatePlaceholderPhone(String openid) {
        int hash = Math.abs(openid.hashCode());
        return "000" + String.format("%08d", hash % 100000000);
    }
}
