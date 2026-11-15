package com.guardian.mapper;

import com.guardian.entity.Alert;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 健康告警数据访问
 */
@Mapper
public interface AlertMapper {

    /** 查询某用户在时间区间内发生的告警（用于健康评分） */
    @Select("""
        SELECT * FROM alerts
        WHERE user_id = #{userId}
          AND occurred_at >= #{from}
          AND occurred_at < #{to}
        ORDER BY occurred_at DESC
        """)
    List<Alert> selectByUserAndRange(@Param("userId") String userId,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    /** 分页查询告警列表，handled 为 null 时不过滤处理状态 */
    @Select("""
        <script>
        SELECT * FROM alerts
        WHERE user_id = #{userId}
        <if test="handled != null">AND handled = #{handled}</if>
        ORDER BY occurred_at DESC
        LIMIT #{limit} OFFSET #{offset}
        </script>
        """)
    List<Alert> selectPage(@Param("userId") String userId,
                           @Param("handled") Boolean handled,
                           @Param("offset") int offset,
                           @Param("limit") int limit);

    /** 统计告警总数，handled 为 null 时统计全部 */
    @Select("""
        <script>
        SELECT COUNT(*) FROM alerts
        WHERE user_id = #{userId}
        <if test="handled != null">AND handled = #{handled}</if>
        </script>
        """)
    long countByUser(@Param("userId") String userId,
                     @Param("handled") Boolean handled);

    /** 按告警ID查询 */
    @Select("SELECT * FROM alerts WHERE id = #{id}")
    Alert selectById(@Param("id") String id);

    /** 新增告警 */
    @Insert("""
        INSERT INTO alerts (id, user_id, alert_type, severity, title, description,
                            occurred_at, handled, handled_by, handled_at, notes)
        VALUES (#{a.id}, #{a.userId}, #{a.alertType}, #{a.severity}, #{a.title}, #{a.description},
                #{a.occurredAt}, #{a.handled}, #{a.handledBy}, #{a.handledAt}, #{a.notes})
        """)
    int insert(@Param("a") Alert a);

    /** 标记告警为已处理（同时写入处理人与备注） */
    @Update("""
        UPDATE alerts
        SET handled = 1,
            handled_by = #{handledBy},
            handled_at = NOW(3),
            notes = #{notes}
        WHERE id = #{id}
        """)
    int updateHandled(@Param("id") String id,
                      @Param("handledBy") String handledBy,
                      @Param("notes") String notes);

    // ========================================================================
    // 以下方法服务于 /api/alert/* 与 /api/feedback/*（按类型集合过滤）
    // ========================================================================

    /**
     * 按类型集合分页查询告警
     *
     * alertTypes 为空时不过滤类型；handled 为 null 时不过滤处理状态。
     */
    @Select("""
        <script>
        SELECT * FROM alerts
        WHERE user_id = #{userId}
        <if test="handled != null">AND handled = #{handled}</if>
        <if test="alertTypes != null and alertTypes.size() > 0">
          AND alert_type IN
          <foreach collection="alertTypes" item="t" open="(" separator="," close=")">#{t}</foreach>
        </if>
        ORDER BY occurred_at DESC
        LIMIT #{limit} OFFSET #{offset}
        </script>
        """)
    List<Alert> selectPageByTypes(@Param("userId") String userId,
                                  @Param("alertTypes") List<String> alertTypes,
                                  @Param("handled") Boolean handled,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    /** 按类型集合统计总数（配合 selectPageByTypes 求 total） */
    @Select("""
        <script>
        SELECT COUNT(*) FROM alerts
        WHERE user_id = #{userId}
        <if test="handled != null">AND handled = #{handled}</if>
        <if test="alertTypes != null and alertTypes.size() > 0">
          AND alert_type IN
          <foreach collection="alertTypes" item="t" open="(" separator="," close=")">#{t}</foreach>
        </if>
        </script>
        """)
    long countByTypes(@Param("userId") String userId,
                      @Param("alertTypes") List<String> alertTypes,
                      @Param("handled") Boolean handled);

    /** 按类型集合 + 时间区间查询（用于统计） */
    @Select("""
        <script>
        SELECT * FROM alerts
        WHERE user_id = #{userId}
          AND occurred_at >= #{from}
          AND occurred_at &lt; #{to}
        <if test="alertTypes != null and alertTypes.size() > 0">
          AND alert_type IN
          <foreach collection="alertTypes" item="t" open="(" separator="," close=")">#{t}</foreach>
        </if>
        ORDER BY occurred_at DESC
        </script>
        """)
    List<Alert> selectByTypesAndRange(@Param("userId") String userId,
                                      @Param("alertTypes") List<String> alertTypes,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    /** 按日期分组的告警数量（用于趋势图） */
    @Select("""
        <script>
        SELECT DATE(occurred_at) AS date, COUNT(*) AS cnt
        FROM alerts
        WHERE user_id = #{userId}
          AND occurred_at >= #{from}
          AND occurred_at &lt; #{to}
        <if test="alertTypes != null and alertTypes.size() > 0">
          AND alert_type IN
          <foreach collection="alertTypes" item="t" open="(" separator="," close=")">#{t}</foreach>
        </if>
        GROUP BY DATE(occurred_at)
        ORDER BY date ASC
        </script>
        """)
    List<Map<String, Object>> countGroupByDate(@Param("userId") String userId,
                                               @Param("alertTypes") List<String> alertTypes,
                                               @Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to);

    /**
     * 分页查询「与我相关」的告警（本人 + 已接受绑定的长辈）
     *
     * 家属端要能一次性看到所有绑定长辈的告警，避免在 Java 侧逐个用户查询。
     */
    @Select("""
        <script>
        SELECT * FROM alerts
        WHERE user_id IN
        <foreach collection="userIds" item="u" open="(" separator="," close=")">#{u}</foreach>
        <if test="alertTypes != null and alertTypes.size() > 0">
          AND alert_type IN
          <foreach collection="alertTypes" item="t" open="(" separator="," close=")">#{t}</foreach>
        </if>
        <if test="handled != null">AND handled = #{handled}</if>
        ORDER BY occurred_at DESC
        LIMIT #{limit} OFFSET #{offset}
        </script>
        """)
    List<Alert> selectPageByUsers(@Param("userIds") List<String> userIds,
                                  @Param("alertTypes") List<String> alertTypes,
                                  @Param("handled") Boolean handled,
                                  @Param("offset") int offset,
                                  @Param("limit") int limit);

    /** 统计「与我相关」的告警总数 */
    @Select("""
        <script>
        SELECT COUNT(*) FROM alerts
        WHERE user_id IN
        <foreach collection="userIds" item="u" open="(" separator="," close=")">#{u}</foreach>
        <if test="alertTypes != null and alertTypes.size() > 0">
          AND alert_type IN
          <foreach collection="alertTypes" item="t" open="(" separator="," close=")">#{t}</foreach>
        </if>
        <if test="handled != null">AND handled = #{handled}</if>
        </script>
        """)
    long countByUsers(@Param("userIds") List<String> userIds,
                      @Param("alertTypes") List<String> alertTypes,
                      @Param("handled") Boolean handled);
}
