package com.guardian.mapper;

import com.guardian.entity.User;
import org.apache.ibatis.annotations.*;

/**
 * 用户数据访问
 */
@Mapper
public interface UserMapper {

    @Select("SELECT * FROM users WHERE id = #{id}")
    User selectById(@Param("id") String id);

    @Select("SELECT * FROM users WHERE phone = #{phone}")
    User selectByPhone(@Param("phone") String phone);

    @Insert("""
        INSERT INTO users (id, phone, name, avatar, role, birth_date, gender,
                           address, emergency_phone, is_active)
        VALUES (#{u.id}, #{u.phone}, #{u.name}, #{u.avatar}, #{u.role},
                #{u.birthDate}, #{u.gender}, #{u.address}, #{u.emergencyPhone},
                #{u.isActive})
        """)
    int insert(@Param("u") User u);

    @Update("""
        <script>
        UPDATE users
        <set>
          <if test="u.name != null">name = #{u.name},</if>
          <if test="u.avatar != null">avatar = #{u.avatar},</if>
          <if test="u.role != null">role = #{u.role},</if>
          <if test="u.birthDate != null">birth_date = #{u.birthDate},</if>
          <if test="u.gender != null">gender = #{u.gender},</if>
          <if test="u.address != null">address = #{u.address},</if>
          <if test="u.emergencyPhone != null">emergency_phone = #{u.emergencyPhone},</if>
          <if test="u.isActive != null">is_active = #{u.isActive},</if>
          updated_at = NOW(3)
        </set>
        WHERE id = #{u.id}
        </script>
        """)
    int update(@Param("u") User u);

    @Select("SELECT COUNT(*) FROM users WHERE phone = #{phone}")
    long countByPhone(@Param("phone") String phone);

    // ========================================================================
    // 后台管理
    // ========================================================================

    /**
     * 分页查询用户（后台用）
     *
     * keyword 同时匹配手机号和姓名；role / isAdmin 为可选筛选。
     */
    @Select("""
        <script>
        SELECT * FROM users
        <where>
          <if test="keyword != null and keyword != ''">
            AND (phone LIKE CONCAT('%', #{keyword}, '%')
                 OR name LIKE CONCAT('%', #{keyword}, '%'))
          </if>
          <if test="role != null and role != ''">AND role = #{role}</if>
          <if test="isAdmin != null">AND is_admin = #{isAdmin}</if>
          <if test="isActive != null">AND is_active = #{isActive}</if>
        </where>
        ORDER BY created_at DESC
        LIMIT #{offset}, #{pageSize}
        </script>
        """)
    java.util.List<User> selectPage(@Param("keyword") String keyword,
                                    @Param("role") String role,
                                    @Param("isAdmin") Boolean isAdmin,
                                    @Param("isActive") Boolean isActive,
                                    @Param("offset") int offset,
                                    @Param("pageSize") int pageSize);

    @Select("""
        <script>
        SELECT COUNT(*) FROM users
        <where>
          <if test="keyword != null and keyword != ''">
            AND (phone LIKE CONCAT('%', #{keyword}, '%')
                 OR name LIKE CONCAT('%', #{keyword}, '%'))
          </if>
          <if test="role != null and role != ''">AND role = #{role}</if>
          <if test="isAdmin != null">AND is_admin = #{isAdmin}</if>
          <if test="isActive != null">AND is_active = #{isActive}</if>
        </where>
        </script>
        """)
    long countPage(@Param("keyword") String keyword,
                   @Param("role") String role,
                   @Param("isAdmin") Boolean isAdmin,
                   @Param("isActive") Boolean isActive);

    /** 设置管理员标记 */
    @Update("UPDATE users SET is_admin = #{isAdmin}, updated_at = NOW(3) WHERE id = #{id}")
    int updateAdmin(@Param("id") String id, @Param("isAdmin") Boolean isAdmin);

    /** 启用 / 禁用账号 */
    @Update("UPDATE users SET is_active = #{isActive}, updated_at = NOW(3) WHERE id = #{id}")
    int updateActive(@Param("id") String id, @Param("isActive") Boolean isActive);

    /** 按角色统计 */
    @Select("SELECT COUNT(*) FROM users WHERE role = #{role}")
    long countByRole(@Param("role") String role);

    /** 按管理员标记统计 */
    @Select("SELECT COUNT(*) FROM users WHERE is_admin = #{isAdmin}")
    long countByAdmin(@Param("isAdmin") Boolean isAdmin);

    /** 某日之后注册的用户数 */
    @Select("SELECT COUNT(*) FROM users WHERE created_at >= #{since}")
    long countSince(@Param("since") java.time.LocalDateTime since);
}
