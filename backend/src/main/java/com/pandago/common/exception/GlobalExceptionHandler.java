package com.pandago.common.exception;

import com.pandago.common.api.Result;
import com.pandago.common.api.ResultCode;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理。
 *
 * <p>把各处抛出的异常统一转换为 {@link Result} 结构：
 * 客户端只看到可读提示，堆栈 / SQL / 内部连接信息只写服务端日志，不返回前端。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：错误码同时映射到 HTTP 状态码，便于前端按状态处理 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        log.warn("业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        HttpStatus status = switch (ex.getCode()) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(Result.fail(ex.getCode(), ex.getMessage()));
    }

    /**
     * 方法级鉴权失败（@PreAuthorize 的 AccessDeniedException）。
     *
     * <p>它发生在 Controller 方法调用阶段，会被本 Advice 捕获，若不单独处理就会被
     * 兜底 Exception 处理器转成 500。URL 级的鉴权失败不会走到这里，而是由
     * RestAccessDeniedHandler 处理；两者都统一输出 403 的 Result。</p>
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> handleAccessDenied(AccessDeniedException ex) {
        log.warn("方法级权限不足: {}", ex.getMessage());
        return Result.fail(ResultCode.FORBIDDEN, "没有权限访问该资源");
    }

    /** 参数校验失败：拼接字段名与错误提示，返回 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.BAD_REQUEST, message);
    }

    /** 查询参数对象（@ModelAttribute）校验失败：同样返回 400 */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.BAD_REQUEST, message);
    }

    /** 方法参数校验失败（@RequestParam 上的 @Min / @Max 等）：返回 400 */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.BAD_REQUEST, message);
    }

    /**
     * 资源不存在（Spring Boot 3.2+ 对未映射静态资源抛 NoResourceFoundException）。
     *
     * <p>若不单独处理会被兜底 Exception 转成 500；统一映射为 404，语义正确且前端可按状态处理。</p>
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResourceFound(NoResourceFoundException ex) {
        return Result.fail(ResultCode.NOT_FOUND, "资源不存在");
    }

    /**
     * 文件上传超过 Spring multipart 上限。
     *
     * <p>配置上限略大于业务上限（头像 2MB，multipart 3MB），
     * 这里返回 400 让前端显示"文件过大"而不是走未知异常 500。</p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return Result.fail(ResultCode.BAD_REQUEST, "上传文件过大，头像图片不能超过 2MB");
    }

    /** 缺少 multipart 字段名（file）或请求不是 multipart/form-data */
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleMissingFilePart(Exception ex) {
        return Result.fail(ResultCode.BAD_REQUEST, "请选择要上传的图片，字段名应为 file");
    }

    /** 其他未知异常：记录完整堆栈，返回通用提示，不泄露内部细节 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleUnexpectedException(Exception ex) {
        log.error("未预期的系统异常", ex);
        return Result.fail(ResultCode.INTERNAL_ERROR);
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }
}
