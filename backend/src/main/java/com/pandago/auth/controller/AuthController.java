package com.pandago.auth.controller;

import com.pandago.auth.dto.LoginDTO;
import com.pandago.auth.dto.RegisterDTO;
import com.pandago.auth.service.AuthService;
import com.pandago.auth.vo.LoginVO;
import com.pandago.auth.vo.UserInfoVO;
import com.pandago.common.api.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。注册与登录为公开接口，/me 需登录（由 SecurityConfig 保证）。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 注册：成功后返回新用户 id，前端通常跳转到登录页 */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.ok(authService.register(dto));
    }

    /** 登录：返回 JWT 与展示信息 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.ok(authService.login(dto));
    }

    /** 当前登录用户信息（身份来自 SecurityContext，不接受任何 userId 参数） */
    @GetMapping("/me")
    public Result<UserInfoVO> me() {
        return Result.ok(authService.currentUser());
    }
}
