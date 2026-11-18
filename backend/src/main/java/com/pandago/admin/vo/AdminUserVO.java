package com.pandago.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 管理端用户列表 VO（阶段 8）：不返回密码哈希。 */
@Data
public class AdminUserVO {
    private Long id;
    private String username;
    private String nickname;
    private String phone;
    /** 1 启用 0 禁用 */
    private Integer status;
    /** 角色编码：USER / MERCHANT / ADMIN */
    private List<String> roles;
    private LocalDateTime createdAt;
}
