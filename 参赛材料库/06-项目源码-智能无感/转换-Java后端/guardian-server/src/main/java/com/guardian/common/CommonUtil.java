package com.guardian.common;

import cn.hutool.core.util.IdUtil;

import java.util.regex.Pattern;

/**
 * 通用工具
 */
public final class CommonUtil {

    private CommonUtil() {}

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    /** 是否为中国大陆手机号 */
    public static boolean isPhone(String s) {
        return s != null && PHONE.matcher(s).matches();
    }

    /** 生成 36 位 UUID（去掉横线），与数据库 varchar(36) 对齐 */
    public static String uuid() {
        return IdUtil.fastSimpleUUID();
    }

    /**
     * 手机号脱敏：138****1234
     *
     * 日志和接口返回里都用这个，避免泄露完整号码
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 姓名脱敏：张* */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.length() == 1) return name;
        return name.charAt(0) + "*".repeat(name.length() - 1);
    }

    /** 生成 6 位数字验证码 */
    public static String smsCode() {
        return String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
    }

    /** 生成 8 位邀请码（大写字母 + 数字，去掉易混的 0/O/1/I） */
    public static String inviteCode() {
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt((int) (Math.random() * chars.length())));
        }
        return sb.toString();
    }
}
