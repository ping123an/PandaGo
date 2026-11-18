package com.pandago.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security 核心配置（阶段 3）。
 *
 * <p>要点：
 * 1. STATELESS：不创建 HttpSession，身份完全由 JWT 承载，适合前后端分离。
 * 2. csrf 关闭：Token 认证不依赖 Cookie，且写接口带 Authorization 头，无需 CSRF 令牌。
 * 3. 公开接口白名单：注册、登录、健康检查、商品/分类的读接口；其余默认需认证。
 * 4. 角色规则：/admin/** 仅 ADMIN；/merchant/** 允许 MERCHANT 与 ADMIN。
 * 5. 401 / 403 交给自定义处理器，输出统一 Result，避免 Spring 默认 HTML/空响应。
 * 6. JwtAuthenticationFilter 插在 UsernamePasswordAuthenticationFilter 之前。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 复用已有 CORS 配置（CorsConfig 中按来源白名单配置）
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // REST API 无 Cookie 会话，关闭 CSRF
                .csrf(csrf -> csrf.disable())
                // 无状态会话：不创建、不使用 HttpSession
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 关闭默认表单登录与 HTTP Basic，避免弹窗/重定向干扰统一响应
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        // 预检请求直接放行
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 认证相关公开接口
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login"
                        ).permitAll()
                        // 健康检查与监控
                        .requestMatchers("/api/v1/health", "/actuator/**").permitAll()
                        // 静态图片资源
                        .requestMatchers("/images/**", "/uploads/**", "/favicon.ico", "/error").permitAll()
                        // 用户端只读接口：游客可浏览商品、分类与商品评价
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/categories",
                                "/api/v1/products",
                                "/api/v1/products/*",
                                "/api/v1/reviews/products/*"
                        ).permitAll()
                        // 管理端：仅 ADMIN
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        // 商家商品端：本阶段严格要求 MERCHANT，管理员审核走 /admin/**
                        .requestMatchers("/api/v1/merchant/products/**").hasRole("MERCHANT")
                        // 商家端其他入口：MERCHANT 或 ADMIN
                        .requestMatchers("/api/v1/merchant/**").hasAnyRole("MERCHANT", "ADMIN")
                        // 其余接口（含 /auth/me）需登录
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                // JWT 过滤器在用户名密码认证过滤器之前执行
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** BCrypt 密码编码器：注册加密、登录校验共用同一实例 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 暴露 AuthenticationManager，便于未来扩展用户名密码认证流程 */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
