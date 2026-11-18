package com.pandago.admin.controller;

import com.pandago.admin.service.AdminDashboardService;
import com.pandago.admin.vo.AdminDashboardVO;
import com.pandago.common.api.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台管理工作台接口。
 *
 * <p>三重权限保护：URL 规则 /api/v1/admin/** 仅 ADMIN，方法 @PreAuthorize，
 * Service 层再调用 SecurityUtils.requireRole(ADMIN)。接口无入参，
 * 不接受前端传入的管理员 ID 或统计数值。</p>
 */
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    public Result<AdminDashboardVO> dashboard() {
        return Result.ok(adminDashboardService.dashboard());
    }
}
