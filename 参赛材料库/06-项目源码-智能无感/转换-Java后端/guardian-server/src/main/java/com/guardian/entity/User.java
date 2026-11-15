package com.guardian.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户（长辈 / 家属）
 *
 * 对应表：users
 */
@Data
public class User implements Serializable {
    /** 用户ID（UUID） */
    private String id;

    /** 手机号 */
    private String phone;

    /** 姓名 */
    private String name;

    /** 头像URL */
    private String avatar;

    /** 角色：elder 长辈 / family 家属 */
    private String role;

    /** 出生日期 */
    private LocalDate birthDate;

    /** 性别 */
    private String gender;

    /** 地址 */
    private String address;

    /** 紧急联系电话 */
    private String emergencyPhone;

    /** 是否启用 */
    private Boolean isActive;

    /** 是否管理员（可登录后台管理） */
    private Boolean isAdmin;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
