package com.guardian.mapper;

import com.guardian.entity.CheckinRecord;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 作息打卡数据访问
 */
@Mapper
public interface CheckinRecordMapper {

    @Select("""
        SELECT * FROM checkin_records
        WHERE user_id = #{userId} AND date BETWEEN #{from} AND #{to}
        ORDER BY date DESC
        """)
    List<CheckinRecord> selectByUserAndRange(@Param("userId") String userId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    @Select("SELECT * FROM checkin_records WHERE user_id = #{userId} AND date = #{date}")
    CheckinRecord selectByUserAndDate(@Param("userId") String userId,
                                      @Param("date") LocalDate date);

    /**
     * 插入或更新（同一天只保留一条）
     *
     * 用 ON DUPLICATE KEY UPDATE 依赖 uk_checkin_user_date 唯一索引，
     * 避免「先查再写」的并发问题。
     */
    @Insert("""
        INSERT INTO checkin_records (user_id, date, wake_time, sleep_time, wake_score, sleep_score)
        VALUES (#{r.userId}, #{r.date}, #{r.wakeTime}, #{r.sleepTime}, #{r.wakeScore}, #{r.sleepScore})
        ON DUPLICATE KEY UPDATE
          wake_time  = COALESCE(VALUES(wake_time),  wake_time),
          sleep_time = COALESCE(VALUES(sleep_time), sleep_time),
          wake_score = COALESCE(VALUES(wake_score), wake_score),
          sleep_score= COALESCE(VALUES(sleep_score),sleep_score),
          updated_at = NOW(3)
        """)
    int upsert(@Param("r") CheckinRecord r);

    /** 连续打卡天数统计（从今天往前推算） */
    @Select("""
        SELECT date FROM checkin_records
        WHERE user_id = #{userId}
        ORDER BY date DESC
        LIMIT 60
        """)
    List<LocalDate> selectRecentDates(@Param("userId") String userId);

    /** 统计区间内的打卡天数 */
    @Select("""
        SELECT COUNT(DISTINCT date) FROM checkin_records
        WHERE user_id = #{userId} AND date BETWEEN #{from} AND #{to}
        """)
    int countDays(@Param("userId") String userId,
                  @Param("from") LocalDate from,
                  @Param("to") LocalDate to);

    /** 按日期聚合的睡眠时长（用于报告） */
    @Select("""
        SELECT date, wake_time AS wakeTime, sleep_time AS sleepTime
        FROM checkin_records
        WHERE user_id = #{userId} AND date BETWEEN #{from} AND #{to}
          AND wake_time IS NOT NULL AND sleep_time IS NOT NULL
        ORDER BY date ASC
        """)
    List<Map<String, Object>> selectSleepRaw(@Param("userId") String userId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);
}
