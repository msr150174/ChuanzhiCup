package com.guardian.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 绑定的智能设备
 *
 * 对应表：devices
 */
@Data
public class Device implements Serializable {
    /** 设备ID */
    private String id;

    /** 所属用户 */
    private String userId;

    /** 设备名称 */
    private String deviceName;

    /** 设备类型 */
    private String deviceType;

    /** 设备MAC地址 */
    private String deviceMac;

    /** 状态 */
    private String status;

    /** 最后同步时间 */
    private LocalDateTime lastSync;

    /** 扩展信息(JSON) */
    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
