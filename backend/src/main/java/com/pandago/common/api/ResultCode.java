package com.pandago.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 全局业务错误码。
 *
 * <p>约定：2xx 成功；4xx 客户端错误；5xx 服务端错误。
 * 业务模块自定义错误码时，应在本枚举中集中登记，禁止各处随意定义
 * 不兼容的码值，保证前端可以按统一约定处理。</p>
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "成功"),

    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "业务冲突，操作无法完成"),

    INTERNAL_ERROR(500, "系统繁忙，请稍后再试");

    private final int code;
    private final String message;
}
