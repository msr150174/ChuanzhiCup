package com.guardian.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 运动记录
 *
 * 对应表：exercise_records
 */
@Data
public class ExerciseRecord implements Serializable {
    /** 记录ID */
    private String id;

    /** 用户ID */
    private String userId;

    /** 日期 */
    private LocalDate date;

    /** 运动类型 */
    private String exerciseType;

    /** 时长（分钟） */
    private Integer duration;

    /** 步数 */
    private Integer steps;

    /** 消耗热量 kcal */
    private Integer calories;

    /** 距离 km */
    private BigDecimal distance;

    /** 强度 */
    private String intensity;

    /** 备注 */
    private String notes;

    private LocalDateTime createdAt;
}
