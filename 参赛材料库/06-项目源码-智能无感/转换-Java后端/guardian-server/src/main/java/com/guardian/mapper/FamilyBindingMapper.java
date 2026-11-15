package com.guardian.mapper;

import com.guardian.entity.FamilyBinding;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 长辈-家属绑定关系数据访问
 */
@Mapper
public interface FamilyBindingMapper {

    /** 查询某长辈的全部绑定关系 */
    @Select("""
        SELECT * FROM family_bindings
        WHERE elder_id = #{elderId}
        ORDER BY created_at DESC
        """)
    List<FamilyBinding> selectByElder(@Param("elderId") String elderId);

    /** 查询某家属的全部绑定关系 */
    @Select("""
        SELECT * FROM family_bindings
        WHERE family_id = #{familyId}
        ORDER BY created_at DESC
        """)
    List<FamilyBinding> selectByFamily(@Param("familyId") String familyId);

    /**
     * 按邀请码查询绑定关系
     *
     * 同一邀请码可能存在多条历史记录，取最新一条。
     */
    @Select("""
        SELECT * FROM family_bindings
        WHERE invite_code = #{inviteCode}
        ORDER BY created_at DESC
        LIMIT 1
        """)
    FamilyBinding selectByInviteCode(@Param("inviteCode") String inviteCode);

    /** 新增绑定关系 */
    @Insert("""
        INSERT INTO family_bindings (id, elder_id, family_id, invite_code, status)
        VALUES (#{b.id}, #{b.elderId}, #{b.familyId}, #{b.inviteCode}, #{b.status})
        """)
    int insert(@Param("b") FamilyBinding b);

    /** 更新绑定状态：pending / accepted / rejected */
    @Update("""
        UPDATE family_bindings
        SET status = #{status}, updated_at = NOW(3)
        WHERE id = #{id}
        """)
    int updateStatus(@Param("id") String id,
                     @Param("status") String status);

    /**
     * 接受邀请：同时补上 family_id 并改状态
     *
     * 邀请时对方可能还没注册，family_id 是空的；
     * 等他注册后凭邀请码接受，这里把 family_id 补上。
     */
    @Update("""
        UPDATE family_bindings
        SET family_id = #{familyId}, status = 'accepted', updated_at = NOW(3)
        WHERE id = #{id}
        """)
    int acceptWithFamily(@Param("id") String id,
                         @Param("familyId") String familyId);

    /** 删除绑定关系（物理删除） */
    @Delete("DELETE FROM family_bindings WHERE id = #{id}")
    int deleteById(@Param("id") String id);

    /**
     * 查询某长辈已接受的家属成员，并关联 users 表取姓名与手机号
     */
    @Select("""
        SELECT b.id          AS bindingId,
               b.elder_id    AS elderId,
               b.family_id   AS familyId,
               b.status      AS status,
               b.created_at  AS createdAt,
               u.name        AS name,
               u.phone       AS phone,
               u.avatar      AS avatar
        FROM family_bindings b
        JOIN users u ON u.id = b.family_id
        WHERE b.elder_id = #{elderId} AND b.status = 'accepted'
        ORDER BY b.created_at DESC
        """)
    List<Map<String, Object>> selectMembersWithUser(@Param("elderId") String elderId);
}
