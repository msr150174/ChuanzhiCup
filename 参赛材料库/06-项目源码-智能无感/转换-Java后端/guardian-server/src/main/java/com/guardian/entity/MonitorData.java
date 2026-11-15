package com.guardian.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 健康监测数据
 *
 * 对应表：monitor_data
 */
@Data
public class MonitorData implements Serializable {
    private Long id;

    /** 用户ID */
    private String userId;

    /** 来源设备 */
    private String deviceId;

    /** 数据类型 */
    private String dataType;

    /** 数值 */
    private BigDecimal value;

    /** 单位 */
    private String unit;

    /** 采集时间 */
    private LocalDateTime recordedAt;

    /** 扩展信息(JSON) */
    private String metadata;

    private LocalDateTime createdAt;
}
