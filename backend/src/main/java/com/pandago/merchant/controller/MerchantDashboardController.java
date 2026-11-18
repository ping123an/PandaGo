package com.pandago.merchant.controller;

import com.pandago.common.api.Result;
import com.pandago.merchant.service.MerchantDashboardService;
import com.pandago.merchant.vo.MerchantDashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家工作台统计接口（V9）。
 *
 * <p>类级 @PreAuthorize 先挡住普通用户；Service 再校验商家角色并从 SecurityContext
 * 读取商家 ID。URL 不支持传 merchantId，从根本上杜绝越权统计。</p>
 */
@Validated
@PreAuthorize("hasRole('MERCHANT')")
@RestController
@RequestMapping("/api/v1/merchant/dashboard")
@RequiredArgsConstructor
public class MerchantDashboardController {

    private final MerchantDashboardService merchantDashboardService;

    /** 当前登录商家的经营统计 */
    @GetMapping
    public Result<MerchantDashboardVO> dashboard() {
        return Result.ok(merchantDashboardService.dashboard());
    }
}
