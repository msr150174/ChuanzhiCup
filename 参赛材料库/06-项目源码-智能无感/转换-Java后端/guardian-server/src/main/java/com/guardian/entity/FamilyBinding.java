package com.guardian.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 长辈-家属绑定关系
 *
 * 对应表：family_bindings
 */
@Data
public class FamilyBinding implements Serializable {
    /** 绑定ID */
    private String id;

    /** 长辈用户ID */
    private String elderId;

    /** 家属用户ID */
    private String familyId;

    /** 邀请码 */
    private String inviteCode;

    /** 状态 */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
