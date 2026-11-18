package com.pandago.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 登录请求 DTO。
 *
 * <p>{@code loginAs} 是「身份选择页」传来的**登录意图**（USER / MERCHANT / ADMIN），
 * 仅用于告诉后端"用户想以哪个身份进入"。它绝不决定真实权限——后端会拿数据库中
 * 该账号的实际角色做校验，不匹配则拒绝。该字段可选：老前端/接口不传时不做身份校验，
 * 保持向后兼容。权限的最终来源始终是数据库角色 + Spring Security。</p>
 */
@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 可选登录身份：USER / MERCHANT / ADMIN；不传则不做身份匹配校验 */
    @Pattern(regexp = "USER|MERCHANT|ADMIN", message = "登录身份只能为 USER、MERCHANT 或 ADMIN")
    private String loginAs;
}
