package com.pandago.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求 DTO。
 *
 * <p>不含 roleId / roleCode 等角色字段——角色由服务端强制分配 USER，
 * 客户端无法通过这些字段提权。确认密码用于表单二次校验。</p>
 */
@Data
public class RegisterDTO {

    /** 登录账号：字母开头，允许字母数字下划线，4~30 位 */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 30, message = "用户名长度须在 4~30 之间")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]*$", message = "用户名须以字母开头，只能包含字母、数字和下划线")
    private String username;

    /** 密码：8~64 位，须包含字母和数字 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度须在 8~64 之间")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "密码须同时包含字母和数字")
    private String password;

    /** 确认密码：须与 password 一致（Service 中校验） */
    @NotBlank(message = "请再次输入密码")
    private String confirmPassword;

    /** 昵称，可选 */
    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    /** 手机号，可选 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
