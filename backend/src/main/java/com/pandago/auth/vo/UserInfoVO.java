package com.pandago.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** 当前登录用户信息 VO（GET /auth/me）。不含任何密码字段。 */
@Data
@Builder
public class UserInfoVO {

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    private String phone;

    /** 角色编码，如 ["USER"] */
    private List<String> roles;
}
