package com.pandago.security;

import lombok.Getter;

/**
 * 放入 SecurityContext 的登录主体。
 *
 * <p>只包含业务需要的非敏感字段（id / username），不携带密码或 Token 原文。
 * Controller 通过 {@link SecurityUtils} 获取当前登录用户。</p>
 */
@Getter
public class LoginUserPrincipal {

    private final Long userId;
    private final String username;

    public LoginUserPrincipal(Long userId, String username) {
        this.userId = userId;
        this.username = username;
    }
}
