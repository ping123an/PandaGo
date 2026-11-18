package com.pandago.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 受控角色编码。与 role.code 列一一对应。
 *
 * <p>注册流程只能分配 USER；MERCHANT / ADMIN 只能通过受控初始化或管理流程分配，
 * 绝不读取客户端传来的角色参数。Spring Security 的角色前缀 ROLE_ 在转换为
 * GrantedAuthority 时统一添加，避免在业务代码里散落字符串拼接。</p>
 */
@Getter
@AllArgsConstructor
public enum RoleCode {

    USER("USER", "普通用户"),
    MERCHANT("MERCHANT", "商家"),
    ADMIN("ADMIN", "管理员");

    /** 存入数据库 role.code 与 JWT claims 的值 */
    @EnumValue
    private final String code;
    private final String description;

    /** Spring Security 的 GrantedAuthority 形式 */
    public String authority() {
        return "ROLE_" + code;
    }

    public static RoleCode fromCode(String code) {
        for (RoleCode value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
