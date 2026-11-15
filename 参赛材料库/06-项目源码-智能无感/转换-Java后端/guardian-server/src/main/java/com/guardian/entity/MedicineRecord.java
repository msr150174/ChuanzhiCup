package com.guardian.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 服药记录
 *
 * 对应表：medicine_records
 */
@Data
public class MedicineRecord implements Serializable {
    /** 记录ID */
    private String id;

    /** 关联的提醒 */
    private String reminderId;

    /** 用户ID */
    private String userId;

    /** 药品名称 */
    private String medicineName;

    /** 服药时间 */
    private LocalDateTime takenTime;

    /** 服药日期 */
    private LocalDate takenDate;

    /** 状态 */
    private String status;

    /** 备注 */
    private String notes;

    private LocalDateTime createdAt;
}
