package com.pandago.common.api;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 统一接口响应结构。
 *
 * <p>所有 Controller 均返回该结构，字段固定为 code / message / data / timestamp，
 * 前端 Axios 拦截器据此统一判断成功与失败。禁止不同模块各自定义响应格式。</p>
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务状态码，参见 {@link ResultCode} */
    private int code;

    /** 给客户端看的提示信息，不包含堆栈、SQL 等内部细节 */
    private String message;

    /** 业务数据 */
    private T data;

    /** 服务端响应时间 */
    private LocalDateTime timestamp;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = LocalDateTime.now();
    }

    /** 成功响应（携带数据） */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    /** 成功响应（无数据） */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /** 失败响应（使用统一错误码） */
    public static <T> Result<T> fail(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    /** 失败响应（自定义提示，用于携带可读的业务原因） */
    public static <T> Result<T> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    /** 失败响应（直接指定码与提示） */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 是否成功 */
    public boolean isSuccess() {
        return this.code == ResultCode.SUCCESS.getCode();
    }
}
