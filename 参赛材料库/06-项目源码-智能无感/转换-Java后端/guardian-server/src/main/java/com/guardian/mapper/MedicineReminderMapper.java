package com.guardian.mapper;

import com.guardian.entity.MedicineReminder;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用药提醒数据访问
 */
@Mapper
public interface MedicineReminderMapper {

    /** 查询某用户的用药提醒，isActive 为 null 时查询全部（含已停用） */
    @Select("""
        <script>
        SELECT * FROM medicine_reminders
        WHERE user_id = #{userId}
        <if test="isActive != null">AND is_active = #{isActive}</if>
        ORDER BY created_at DESC
        </script>
        """)
    List<MedicineReminder> selectByUser(@Param("userId") String userId,
                                        @Param("isActive") Boolean isActive);

    /** 按提醒ID查询 */
    @Select("SELECT * FROM medicine_reminders WHERE id = #{id}")
    MedicineReminder selectById(@Param("id") String id);

    /** 新增用药提醒 */
    @Insert("""
        INSERT INTO medicine_reminders (id, user_id, medicine_name, dosage, frequency, times,
                                        start_date, end_date, notes, is_active)
        VALUES (#{r.id}, #{r.userId}, #{r.medicineName}, #{r.dosage}, #{r.frequency}, #{r.times},
                #{r.startDate}, #{r.endDate}, #{r.notes}, #{r.isActive})
        """)
    int insert(@Param("r") MedicineReminder r);

    /** 动态更新用药提醒（仅更新非空字段） */
    @Update("""
        <script>
        UPDATE medicine_reminders
        <set>
          <if test="r.medicineName != null">medicine_name = #{r.medicineName},</if>
          <if test="r.dosage != null">dosage = #{r.dosage},</if>
          <if test="r.frequency != null">frequency = #{r.frequency},</if>
          <if test="r.times != null">times = #{r.times},</if>
          <if test="r.startDate != null">start_date = #{r.startDate},</if>
          <if test="r.endDate != null">end_date = #{r.endDate},</if>
          <if test="r.notes != null">notes = #{r.notes},</if>
          <if test="r.isActive != null">is_active = #{r.isActive},</if>
          updated_at = NOW(3)
        </set>
        WHERE id = #{r.id}
        </script>
        """)
    int update(@Param("r") MedicineReminder r);

    /** 软删除：将提醒置为停用，保留历史数据 */
    @Update("""
        UPDATE medicine_reminders
        SET is_active = 0, updated_at = NOW(3)
        WHERE id = #{id}
        """)
    int deleteById(@Param("id") String id);
}
