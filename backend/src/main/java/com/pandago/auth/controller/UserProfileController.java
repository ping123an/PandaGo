package com.pandago.auth.controller;

import com.pandago.auth.dto.UserProfileUpdateDTO;
import com.pandago.auth.service.AuthService;
import com.pandago.auth.service.UserAvatarService;
import com.pandago.auth.vo.AvatarUploadVO;
import com.pandago.auth.vo.UserInfoVO;
import com.pandago.common.api.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 当前登录用户的个人资料接口。
 *
 * <p>路径 /api/v1/users/me 由 SecurityConfig 的 anyRequest().authenticated() 兜底保护；
 * Controller 不接收任何 userId 参数，Service 从 SecurityContext 取身份，
 * 因此用户只能读写自己的资料，无法传参修改他人。</p>
 */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final AuthService authService;
    private final UserAvatarService userAvatarService;

    /** 查询当前用户资料（与 /auth/me 复用同一逻辑，VO 不含密码） */
    @GetMapping("/profile")
    public Result<UserInfoVO> profile() {
        return Result.ok(authService.currentUser());
    }

    /** 修改当前用户资料（白名单：昵称 / 手机号），返回更新后的资料 */
    @PutMapping("/profile")
    public Result<UserInfoVO> updateProfile(@Valid @RequestBody UserProfileUpdateDTO dto) {
        return Result.ok(authService.updateMyProfile(dto));
    }

    /**
     * 上传当前用户头像：multipart/form-data，字段名固定为 file。
     *
     * <p>只接收文件，不接收 userId；身份由 JWT 解析，Service 再次校验。
     * 校验与落盘逻辑在 Service，Controller 只负责参数绑定。</p>
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/avatar")
    public Result<AvatarUploadVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.ok(userAvatarService.upload(file));
    }
}
