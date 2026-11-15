package com.guardian.mapper;

import com.guardian.entity.MonitorData;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 健康监测数据访问
 */
@Mapper
public interface MonitorDataMapper {

    /** 分页查询监测数据，dataType 为 null 时查询全部类型，按采集时间倒序 */
    @Select("""
        <script>
        SELECT * FROM monitor_data
        WHERE user_id = #{userId}
        <if test="dataType != null and dataType != ''">AND data_type = #{dataType}</if>
        ORDER BY recorded_at DESC
        LIMIT #{limit} OFFSET #{offset}
        </script>
        """)
    List<MonitorData> selectPage(@Param("userId") String userId,
                                 @Param("dataType") String dataType,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    /** 统计监测数据条数，dataType 为 null 时统计全部类型 */
    @Select("""
        <script>
        SELECT COUNT(*) FROM monitor_data
        WHERE user_id = #{userId}
        <if test="dataType != null and dataType != ''">AND data_type = #{dataType}</if>
        </script>
        """)
    long countByUserAndType(@Param("userId") String userId,
                            @Param("dataType") String dataType);

    /** 新增监测数据（id 自增，无需传入） */
    @Insert("""
        INSERT INTO monitor_data (user_id, device_id, data_type, value, unit, recorded_at, metadata)
        VALUES (#{d.userId}, #{d.deviceId}, #{d.dataType}, #{d.value}, #{d.unit},
                #{d.recordedAt}, #{d.metadata})
        """)
    int insert(@Param("d") MonitorData d);

    /**
     * 取某用户每种数据类型的最新一条
     *
     * 通过 (data_type, recorded_at) 的子查询求最大值定位最新记录，
     * id 作为并列时的兜底排序键。
     */
    @Select("""
        SELECT m.data_type AS dataType,
               m.value      AS value,
               m.unit       AS unit,
               m.device_id  AS deviceId,
               m.recorded_at AS recordedAt
        FROM monitor_data m
        JOIN (
          SELECT data_type, MAX(recorded_at) AS max_recorded
          FROM monitor_data
          WHERE user_id = #{userId}
          GROUP BY data_type
        ) t ON t.data_type = m.data_type AND t.max_recorded = m.recorded_at
        WHERE m.user_id = #{userId}
        ORDER BY m.data_type ASC
        """)
    List<Map<String, Object>> selectLatestByType(@Param("userId") String userId);

    // ========================================================================
    // 以下方法服务于 /api/monitor/* 、/api/werun/* 与 /api/statistics/*
    // ========================================================================

    /** 按时间区间查询监测数据（倒序），dataType 为空时查全部类型 */
    @Select("""
        <script>
        SELECT * FROM monitor_data
        WHERE user_id = #{userId}
          AND recorded_at &gt;= #{from}
          AND recorded_at &lt; #{to}
        <if test="dataType != null and dataType != ''">AND data_type = #{dataType}</if>
        ORDER BY recorded_at DESC
        LIMIT #{limit}
        </script>
        """)
    List<MonitorData> selectByRange(@Param("userId") String userId,
                                    @Param("dataType") String dataType,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    @Param("limit") int limit);

    /** 取某类型最新一条监测数据 */
    @Select("""
        SELECT * FROM monitor_data
        WHERE user_id = #{userId} AND data_type = #{dataType}
        ORDER BY recorded_at DESC
        LIMIT 1
        """)
    MonitorData selectLatestOne(@Param("userId") String userId,
                                @Param("dataType") String dataType);

    /** 按日期汇总某类型的数值（步数等累加型指标用 SUM） */
    @Select("""
        SELECT DATE(recorded_at) AS date, SUM(value) AS total, COUNT(*) AS cnt
        FROM monitor_data
        WHERE user_id = #{userId} AND data_type = #{dataType}
          AND recorded_at >= #{from}
          AND recorded_at < #{to}
        GROUP BY DATE(recorded_at)
        ORDER BY date ASC
        """)
    List<Map<String, Object>> sumGroupByDate(@Param("userId") String userId,
                                             @Param("dataType") String dataType,
                                             @Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);

    /**
     * 某类型在时间区间内的汇总（SUM / AVG / MIN / MAX / COUNT）
     *
     * 无记录时 SUM 与 AVG 为 NULL，调用方需自行兜底。
     */
    @Select("""
        SELECT
          COUNT(*)     AS cnt,
          SUM(value)   AS total,
          AVG(value)   AS avgValue,
          MIN(value)   AS minValue,
          MAX(value)   AS maxValue
        FROM monitor_data
        WHERE user_id = #{userId} AND data_type = #{dataType}
          AND recorded_at >= #{from}
          AND recorded_at < #{to}
        """)
    Map<String, Object> aggregate(@Param("userId") String userId,
                                  @Param("dataType") String dataType,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    /** 各数据类型的记录条数与最新采集时间 */
    @Select("""
        SELECT data_type AS dataType, COUNT(*) AS cnt, MAX(recorded_at) AS lastAt
        FROM monitor_data
        WHERE user_id = #{userId}
        GROUP BY data_type
        ORDER BY cnt DESC
        """)
    List<Map<String, Object>> statsByType(@Param("userId") String userId);
}
