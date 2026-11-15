package com.guardian.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 健康告警
 *
 * 对应表：alerts
 */
@Data
public class Alert implements Serializable {
    /** 告警ID */
    private String id;

    /** 用户ID */
    private String userId;

    /** 告警类型 */
    private String alertType;

    /** 严重程度 */
    private String severity;

    /** 标题 */
    private String title;

    /** 详细描述 */
    private String description;

    /** 发生时间 */
    private LocalDateTime occurredAt;

    /** 是否已处理 */
    private Boolean handled;

    /** 处理人ID */
    private String handledBy;

    /** 处理时间 */
    private LocalDateTime handledAt;

    /** 处理备注 */
    private String notes;

    private LocalDateTime createdAt;
}
