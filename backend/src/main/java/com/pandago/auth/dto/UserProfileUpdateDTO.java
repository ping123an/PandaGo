package com.pandago.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料修改请求（当前登录用户）。
 *
 * <p>白名单设计：只有 nickname / phone 两个可编辑字段。
 * 不含 userId / username / password / status / role 等敏感字段——即使客户端
 * 额外夹带也会被 Jackson 忽略，后端只 set 白名单字段，无法越权改身份或提权。</p>
 */
@Data
public class UserProfileUpdateDTO {

    /** 昵称：必填，1~50 字符，不能仅为空白（@NotBlank 已拒绝纯空格） */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    /** 手机号：可选，留空表示清空；格式沿用注册规则 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
