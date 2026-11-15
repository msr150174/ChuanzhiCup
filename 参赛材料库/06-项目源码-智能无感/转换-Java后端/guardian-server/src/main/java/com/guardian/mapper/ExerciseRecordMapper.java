package com.guardian.mapper;

import com.guardian.entity.ExerciseRecord;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 运动记录数据访问
 */
@Mapper
public interface ExerciseRecordMapper {

    /** 查询某用户在日期区间内的运动记录 */
    @Select("""
        SELECT * FROM exercise_records
        WHERE user_id = #{userId} AND date BETWEEN #{from} AND #{to}
        ORDER BY date DESC, created_at DESC
        """)
    List<ExerciseRecord> selectByUserAndRange(@Param("userId") String userId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    /** 查询某用户某天的运动记录 */
    @Select("""
        SELECT * FROM exercise_records
        WHERE user_id = #{userId} AND date = #{date}
        ORDER BY created_at ASC
        """)
    List<ExerciseRecord> selectByUserAndDate(@Param("userId") String userId,
                                             @Param("date") LocalDate date);

    /** 新增运动记录 */
    @Insert("""
        INSERT INTO exercise_records (id, user_id, date, exercise_type, duration,
                                      steps, calories, distance, intensity, notes)
        VALUES (#{r.id}, #{r.userId}, #{r.date}, #{r.exerciseType}, #{r.duration},
                #{r.steps}, #{r.calories}, #{r.distance}, #{r.intensity}, #{r.notes})
        """)
    int insert(@Param("r") ExerciseRecord r);

    /** 汇总区间内的运动量（无记录时各项为 0） */
    @Select("""
        SELECT
          COALESCE(SUM(steps),    0) AS steps,
          COALESCE(SUM(duration), 0) AS duration,
          COALESCE(SUM(calories), 0) AS calories,
          COALESCE(SUM(distance), 0) AS distance
        FROM exercise_records
        WHERE user_id = #{userId} AND date BETWEEN #{from} AND #{to}
        """)
    Map<String, Object> sumByRange(@Param("userId") String userId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    /** 按运动类型聚合统计（用于「运动类型分布」） */
    @Select("""
        SELECT
          exercise_type               AS exerciseType,
          COUNT(*)                    AS count,
          COALESCE(SUM(duration), 0)  AS duration,
          COALESCE(SUM(steps),    0)  AS steps,
          COALESCE(SUM(calories), 0)  AS calories,
          COALESCE(SUM(distance), 0)  AS distance
        FROM exercise_records
        WHERE user_id = #{userId} AND date BETWEEN #{from} AND #{to}
        GROUP BY exercise_type
        ORDER BY duration DESC
        """)
    List<Map<String, Object>> statsByType(@Param("userId") String userId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    /** 区间内有运动记录的天数（去重） */
    @Select("""
        SELECT COUNT(DISTINCT date) FROM exercise_records
        WHERE user_id = #{userId} AND date BETWEEN #{from} AND #{to}
        """)
    int countDays(@Param("userId") String userId,
                  @Param("from") LocalDate from,
                  @Param("to") LocalDate to);

    /** 删除运动记录 */
    @Delete("DELETE FROM exercise_records WHERE id = #{id} AND user_id = #{userId}")
    int deleteById(@Param("id") String id, @Param("userId") String userId);
}
