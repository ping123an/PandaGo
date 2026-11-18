package com.pandago.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：在每个请求进入 Controller 之前，从 Authorization 头解析 Token，
 * 验证通过后把用户身份写入 SecurityContext。
 *
 * <p>关键设计：认证"成功"才写 SecurityContext；认证失败不在过滤器里 Response，
 * 而是保持匿名，交给 SecurityConfig 的授权规则与 AuthenticationEntryPoint 统一返回 401。
 * 这样无效 Token 与无 Token 的行为一致，且不会被 @RestControllerAdvice 重复处理。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        // 有 Token 且尚未认证（避免覆盖测试注入的身份）、且校验通过 → 写入 SecurityContext
        if (token != null
                && SecurityContextHolder.getContext().getAuthentication() == null
                && jwtTokenProvider.validate(token)) {
            try {
                Long userId = jwtTokenProvider.getUserId(token);
                String username = jwtTokenProvider.getUsername(token);
                List<SimpleGrantedAuthority> authorities = jwtTokenProvider.getRoles(token).stream()
                        .map(role -> new SimpleGrantedAuthority(role.authority()))
                        .toList();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                new LoginUserPrincipal(userId, username), null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                // validate 已通过仍解析失败属于异常数据，清理上下文，按未认证处理
                log.debug("JWT 解析失败，按未认证处理: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /** 从 Authorization: Bearer xxx 提取 Token；格式不符返回 null */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return StringUtils.hasText(token) ? token : null;
        }
        return null;
    }
}
