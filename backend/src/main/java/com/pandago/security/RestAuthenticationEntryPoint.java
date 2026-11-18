package com.pandago.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pandago.common.api.Result;
import com.pandago.common.api.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证（401）统一响应入口。
 *
 * <p>当匿名请求访问受保护接口，或 Token 无效/过期时由 Spring Security 调用。
 * 由于此时请求还未进入 DispatcherServlet，@RestControllerAdvice 不会生效，
 * 因此必须在这里直接写回与业务接口一致的 Result 结构。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.debug("认证失败: {} {}", request.getMethod(), request.getRequestURI());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                Result.fail(ResultCode.UNAUTHORIZED, "未登录或登录已过期，请重新登录"));
    }
}
