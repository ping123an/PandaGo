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
    /**
     * 账号联系电话（页面展示为“联系电话”）。
     * 商家账号在个人中心或商家资料修改手机号时会同步写到这里，
     * 因此平台管理只需展示这一列，不再单独展示 merchant_profile.contact_phone。
     */
    private String phone;
    /** 1 启用 0 禁用 */
    private Integer status;
    /** 角色编码：USER / MERCHANT / ADMIN */
    private List<String> roles;
    private LocalDateTime createdAt;
}
