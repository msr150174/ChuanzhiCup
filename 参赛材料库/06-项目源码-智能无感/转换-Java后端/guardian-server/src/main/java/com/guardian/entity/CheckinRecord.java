package com.guardian.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 作息打卡记录
 *
 * 对应表：checkin_records
 */
@Data
public class CheckinRecord implements Serializable {
    private Long id;

    /** 用户ID */
    private String userId;

    /** 日期 */
    private LocalDate date;

    /** 起床时间 HH:mm */
    private String wakeTime;

    /** 入睡时间 HH:mm */
    private String sleepTime;

    /** 起床评分 */
    private Integer wakeScore;

    /** 入睡评分 */
    private Integer sleepScore;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
