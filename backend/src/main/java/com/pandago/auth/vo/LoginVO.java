package com.pandago.auth.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 登录成功响应 VO。
 *
 * <p>只返回令牌、过期时间与必要的展示信息。绝不包含 password / passwordHash。
 * expiresIn 为毫秒，便于前端在到期前提示续期。</p>
 */
@Data
@Builder
public class LoginVO {

    private String token;

    /** Bearer 令牌类型，前端据此拼 Authorization 头 */
    @Builder.Default
    private String tokenType = "Bearer";

    /** 令牌有效期（毫秒） */
    private long expiresIn;

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    /** 角色编码，如 ["USER"] */
    private List<String> roles;
}
