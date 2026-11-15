package com.guardian.mapper;

import com.guardian.entity.MedicineRecord;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 服药记录数据访问
 */
@Mapper
public interface MedicineRecordMapper {

    /** 查询某用户在日期区间内的服药记录 */
    @Select("""
        SELECT * FROM medicine_records
        WHERE user_id = #{userId} AND taken_date BETWEEN #{from} AND #{to}
        ORDER BY taken_date DESC, taken_time DESC
        """)
    List<MedicineRecord> selectByUserAndRange(@Param("userId") String userId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    /** 查询某用户某天的服药记录 */
    @Select("""
        SELECT * FROM medicine_records
        WHERE user_id = #{userId} AND taken_date = #{date}
        ORDER BY taken_time ASC
        """)
    List<MedicineRecord> selectByUserAndDate(@Param("userId") String userId,
                                             @Param("date") LocalDate date);

    /** 新增服药记录 */
    @Insert("""
        INSERT INTO medicine_records (id, reminder_id, user_id, medicine_name,
                                      taken_time, taken_date, status, notes)
        VALUES (#{r.id}, #{r.reminderId}, #{r.userId}, #{r.medicineName},
                #{r.takenTime}, #{r.takenDate}, #{r.status}, #{r.notes})
        """)
    int insert(@Param("r") MedicineRecord r);

    /**
     * 用药依从性统计
     *
     * 分别统计 taken / missed / skipped 条数，无记录时为 0。
     */
    @Select("""
        SELECT
          SUM(CASE WHEN status = 'taken'   THEN 1 ELSE 0 END) AS taken,
          SUM(CASE WHEN status = 'missed'  THEN 1 ELSE 0 END) AS missed,
          SUM(CASE WHEN status = 'skipped' THEN 1 ELSE 0 END) AS skipped,
          COUNT(*) AS total
        FROM medicine_records
        WHERE user_id = #{userId} AND taken_date BETWEEN #{from} AND #{to}
        """)
    Map<String, Object> adherenceStats(@Param("userId") String userId,
                                       @Param("from") LocalDate from,
                                       @Param("to") LocalDate to);

    /** 查询某用户某提醒在某天的记录（用于同一天重复打卡时更新而非新增） */
    @Select("""
        SELECT * FROM medicine_records
        WHERE user_id = #{userId}
          AND taken_date = #{date}
          AND medicine_name = #{medicineName}
          AND (reminder_id = #{reminderId} OR (#{reminderId} IS NULL AND reminder_id IS NULL))
        ORDER BY taken_time DESC
        LIMIT 1
        """)
    MedicineRecord selectByUserDateAndMedicine(@Param("userId") String userId,
                                               @Param("date") LocalDate date,
                                               @Param("medicineName") String medicineName,
                                               @Param("reminderId") String reminderId);

    /** 更新服药记录状态 */
    @Update("""
        UPDATE medicine_records
        SET status = #{status}, taken_time = #{takenTime}, notes = #{notes}
        WHERE id = #{id}
        """)
    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("takenTime") LocalDateTime takenTime,
                     @Param("notes") String notes);

    /** 删除服药记录 */
    @Delete("DELETE FROM medicine_records WHERE id = #{id} AND user_id = #{userId}")
    int deleteById(@Param("id") String id, @Param("userId") String userId);
}
