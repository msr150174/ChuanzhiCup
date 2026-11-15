package com.guardian.mapper;

import com.guardian.entity.DietRecord;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 饮食记录数据访问
 */
@Mapper
public interface DietRecordMapper {

    /** 查询某用户在日期区间内的饮食记录 */
    @Select("""
        SELECT * FROM diet_records
        WHERE user_id = #{userId} AND date BETWEEN #{from} AND #{to}
        ORDER BY date DESC, meal_time ASC
        """)
    List<DietRecord> selectByUserAndRange(@Param("userId") String userId,
                                          @Param("from") LocalDate from,
                                          @Param("to") LocalDate to);

    /** 查询某用户某天的饮食记录 */
    @Select("""
        SELECT * FROM diet_records
        WHERE user_id = #{userId} AND date = #{date}
        ORDER BY meal_time ASC
        """)
    List<DietRecord> selectByUserAndDate(@Param("userId") String userId,
                                         @Param("date") LocalDate date);

    /** 新增饮食记录 */
    @Insert("""
        INSERT INTO diet_records (id, user_id, date, meal_type, meal_time, foods,
                                  calories, protein, carbs, fat, notes)
        VALUES (#{r.id}, #{r.userId}, #{r.date}, #{r.mealType}, #{r.mealTime}, #{r.foods},
                #{r.calories}, #{r.protein}, #{r.carbs}, #{r.fat}, #{r.notes})
        """)
    int insert(@Param("r") DietRecord r);

    /** 删除饮食记录 */
    @Delete("DELETE FROM diet_records WHERE id = #{id}")
    int deleteById(@Param("id") String id);

    /** 汇总区间内的营养摄入（无记录时各项为 0，通过 COALESCE 保证非空） */
    @Select("""
        SELECT
          COALESCE(SUM(calories), 0) AS calories,
          COALESCE(SUM(protein),  0) AS protein,
          COALESCE(SUM(carbs),    0) AS carbs,
          COALESCE(SUM(fat),      0) AS fat
        FROM diet_records
        WHERE user_id = #{userId} AND date BETWEEN #{from} AND #{to}
        """)
    Map<String, Object> sumNutrition(@Param("userId") String userId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /**
     * 汇总区间内的整体统计
     *
     * days      —— 有饮食记录的天数（去重）
     * meals     —— 记录条数（餐次数量）
     * calories  —— 总热量
     */
    @Select("""
        SELECT
          COUNT(DISTINCT date)        AS days,
          COUNT(*)                    AS meals,
          COALESCE(SUM(calories), 0)  AS calories,
          COALESCE(SUM(protein),  0)  AS protein,
          COALESCE(SUM(carbs),    0)  AS carbs,
          COALESCE(SUM(fat),      0)  AS fat
        FROM diet_records
        WHERE user_id = #{userId} AND date BETWEEN #{from} AND #{to}
        """)
    Map<String, Object> statsByRange(@Param("userId") String userId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /** 按餐次汇总区间内的热量与条数 */
    @Select("""
        SELECT
          meal_type                   AS mealType,
          COUNT(*)                    AS meals,
          COALESCE(SUM(calories), 0)  AS calories
        FROM diet_records
        WHERE user_id = #{userId} AND date BETWEEN #{from} AND #{to}
        GROUP BY meal_type
        ORDER BY meal_type
        """)
    List<Map<String, Object>> statsByMealType(@Param("userId") String userId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);
}
