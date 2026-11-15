package com.guardian.mapper;

import com.guardian.entity.ElderInfo;
import org.apache.ibatis.annotations.*;

/**
 * 长辈健康档案数据访问
 */
@Mapper
public interface ElderInfoMapper {

    /** 按用户ID查询健康档案（一个用户仅一份） */
    @Select("SELECT * FROM elder_info WHERE user_id = #{userId}")
    ElderInfo selectByUser(@Param("userId") String userId);

    /** 新增健康档案 */
    @Insert("""
        INSERT INTO elder_info (id, user_id, blood_type, height, weight,
                                chronic_diseases, allergies, medications,
                                wake_time_target, sleep_time_target, exercise_frequency,
                                diet_preference, smoking, drinking)
        VALUES (#{e.id}, #{e.userId}, #{e.bloodType}, #{e.height}, #{e.weight},
                #{e.chronicDiseases}, #{e.allergies}, #{e.medications},
                #{e.wakeTimeTarget}, #{e.sleepTimeTarget}, #{e.exerciseFrequency},
                #{e.dietPreference}, #{e.smoking}, #{e.drinking})
        """)
    int insert(@Param("e") ElderInfo e);

    /** 动态更新健康档案（仅更新非空字段） */
    @Update("""
        <script>
        UPDATE elder_info
        <set>
          <if test="e.bloodType != null">blood_type = #{e.bloodType},</if>
          <if test="e.height != null">height = #{e.height},</if>
          <if test="e.weight != null">weight = #{e.weight},</if>
          <if test="e.chronicDiseases != null">chronic_diseases = #{e.chronicDiseases},</if>
          <if test="e.allergies != null">allergies = #{e.allergies},</if>
          <if test="e.medications != null">medications = #{e.medications},</if>
          <if test="e.wakeTimeTarget != null">wake_time_target = #{e.wakeTimeTarget},</if>
          <if test="e.sleepTimeTarget != null">sleep_time_target = #{e.sleepTimeTarget},</if>
          <if test="e.exerciseFrequency != null">exercise_frequency = #{e.exerciseFrequency},</if>
          <if test="e.dietPreference != null">diet_preference = #{e.dietPreference},</if>
          <if test="e.smoking != null">smoking = #{e.smoking},</if>
          <if test="e.drinking != null">drinking = #{e.drinking},</if>
          updated_at = NOW(3)
        </set>
        WHERE user_id = #{e.userId}
        </script>
        """)
    int update(@Param("e") ElderInfo e);
}
