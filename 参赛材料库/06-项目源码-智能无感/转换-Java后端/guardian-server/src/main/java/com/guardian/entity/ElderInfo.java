package com.guardian.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 长辈健康档案
 *
 * 对应表：elder_info
 */
@Data
public class ElderInfo implements Serializable {
    /** 档案ID */
    private String id;

    /** 用户ID */
    private String userId;

    /** 血型 */
    private String bloodType;

    /** 身高 cm */
    private BigDecimal height;

    /** 体重 kg */
    private BigDecimal weight;

    /** 慢性病列表(JSON) */
    private String chronicDiseases;

    /** 过敏源列表(JSON) */
    private String allergies;

    /** 长期用药列表(JSON) */
    private String medications;

    /** 目标起床时间 */
    private String wakeTimeTarget;

    /** 目标入睡时间 */
    private String sleepTimeTarget;

    /** 运动频率 */
    private String exerciseFrequency;

    /** 饮食偏好(JSON) */
    private String dietPreference;

    /** 是否吸烟 */
    private Boolean smoking;

    /** 是否饮酒 */
    private Boolean drinking;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
