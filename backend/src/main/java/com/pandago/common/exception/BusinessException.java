package com.pandago.common.exception;

import com.pandago.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>Service 层校验业务规则失败时抛出该异常，携带明确的错误码与提示，
 * 由全局异常处理器转换为统一响应。不要在 Controller 里 try-catch 业务异常后
 * 自行拼装响应，交给 {@link GlobalExceptionHandler} 统一处理。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
