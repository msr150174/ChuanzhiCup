package com.guardian.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用药提醒
 *
 * 对应表：medicine_reminders
 */
@Data
public class MedicineReminder implements Serializable {
    /** 提醒ID */
    private String id;

    /** 用户ID */
    private String userId;

    /** 药品名称 */
    private String medicineName;

    /** 剂量 */
    private String dosage;

    /** 频次 */
    private String frequency;

    /** 提醒时间点列表(JSON) */
    private String times;

    /** 开始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 备注 */
    private String notes;

    /** 是否启用 */
    private Boolean isActive;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
