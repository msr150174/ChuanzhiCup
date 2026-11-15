package com.guardian.mapper;

import com.guardian.entity.Device;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 智能设备数据访问
 */
@Mapper
public interface
DeviceMapper {

    /** 查询某用户绑定的全部设备 */
    @Select("""
        SELECT * FROM devices
        WHERE user_id = #{userId}
        ORDER BY created_at DESC
        """)
    List<Device> selectByUser(@Param("userId") String userId);

    /** 按设备ID查询 */
    @Select("SELECT * FROM devices WHERE id = #{id}")
    Device selectById(@Param("id") String id);

    /** 新增设备 */
    @Insert("""
        INSERT INTO devices (id, user_id, device_name, device_type, device_mac,
                             status, last_sync, metadata)
        VALUES (#{d.id}, #{d.userId}, #{d.deviceName}, #{d.deviceType}, #{d.deviceMac},
                #{d.status}, #{d.lastSync}, #{d.metadata})
        """)
    int insert(@Param("d") Device d);

    /** 动态更新设备（仅更新非空字段） */
    @Update("""
        <script>
        UPDATE devices
        <set>
          <if test="d.userId != null">user_id = #{d.userId},</if>
          <if test="d.deviceName != null">device_name = #{d.deviceName},</if>
          <if test="d.deviceType != null">device_type = #{d.deviceType},</if>
          <if test="d.deviceMac != null">device_mac = #{d.deviceMac},</if>
          <if test="d.status != null">status = #{d.status},</if>
          <if test="d.lastSync != null">last_sync = #{d.lastSync},</if>
          <if test="d.metadata != null">metadata = #{d.metadata},</if>
          updated_at = NOW(3)
        </set>
        WHERE id = #{d.id}
        </script>
        """)
    int update(@Param("d") Device d);

    /** 删除设备（物理删除） */
    @Delete("DELETE FROM devices WHERE id = #{id}")
    int deleteById(@Param("id") String id);

    /** 解绑：仅当设备属于该用户时才删除，返回受影响行数 */
    @Delete("DELETE FROM devices WHERE id = #{id} AND user_id = #{userId}")
    int deleteByIdAndUser(@Param("id") String id, @Param("userId") String userId);

    /** 更新设备状态与最后同步时间 */
    @Update("""
        UPDATE devices
        SET status = #{status}, last_sync = NOW(3), updated_at = NOW(3)
        WHERE id = #{id}
        """)
    int updateStatus(@Param("id") String id, @Param("status") String status);

    /** 更新设备扩展信息（metadata JSON） */
    @Update("""
        UPDATE devices
        SET metadata = #{metadata}, last_sync = NOW(3), updated_at = NOW(3)
        WHERE id = #{id}
        """)
    int updateMetadata(@Param("id") String id, @Param("metadata") String metadata);

    /** 统计某用户在线设备数 */
    @Select("SELECT COUNT(*) FROM devices WHERE user_id = #{userId} AND status = 'active'")
    int countOnline(@Param("userId") String userId);
}
