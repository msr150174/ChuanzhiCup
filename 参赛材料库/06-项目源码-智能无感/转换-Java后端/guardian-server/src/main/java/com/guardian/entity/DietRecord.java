package com.guardian.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 饮食记录
 *
 * 对应表：diet_records
 */
@Data
public class DietRecord implements Serializable {
    /** 记录ID */
    private String id;

    /** 用户ID */
    private String userId;

    /** 日期 */
    private LocalDate date;

    /** 餐次 */
    private String mealType;

    /** 用餐时间 HH:mm */
    private String mealTime;

    /** 食物列表(JSON) */
    private String foods;

    /** 热量 kcal */
    private Integer calories;

    /** 蛋白质 g */
    private Integer protein;

    /** 碳水 g */
    private Integer carbs;

    /** 脂肪 g */
    private Integer fat;

    /** 备注 */
    private String notes;

    private LocalDateTime createdAt;
}
