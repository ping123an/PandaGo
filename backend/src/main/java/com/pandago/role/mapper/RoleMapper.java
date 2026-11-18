package com.pandago.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pandago.role.entity.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /**
     * 通过用户 id 查询其拥有的角色（user_role 中间表 JOIN role 表）。
     *
     * <p>这是明确定义的多表关联查询，参数用 #{userId} 绑定，防止 SQL 注入。
     * 比在业务层先查关联表再逐个查角色更高效（一次查询）。</p>
     */
    @Select("""
            SELECT r.id, r.code, r.name, r.description, r.created_at, r.updated_at
            FROM user_role ur
            INNER JOIN role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            ORDER BY r.id
            """)
    List<Role> selectRolesByUserId(Long userId);
}
