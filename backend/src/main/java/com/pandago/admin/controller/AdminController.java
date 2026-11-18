package com.pandago.admin.controller;

import com.pandago.admin.entity.OperationLog;
import com.pandago.admin.service.AdminOrderService;
import com.pandago.admin.service.AdminUserService;
import com.pandago.admin.service.OperationLogService;
import com.pandago.admin.vo.AdminUserVO;
import com.pandago.common.api.PageResult;
import com.pandago.common.api.Result;
import com.pandago.order.vo.OrderDetailVO;
import com.pandago.order.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端接口（阶段 8）：用户管理、平台订单、操作日志。
 *
 * <p>商品审核与平台商品查询已存在于 ProductController 的 /admin/products 系列，
 * 分类管理在 CategoryController 的 /admin/categories 系列；本 Controller 补齐其余能力。
 * URL 规则（/admin/** 仅 ADMIN）+ 方法级 @PreAuthorize + Service 层 requireRole 三重保护。</p>
 */
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminUserService adminUserService;
    private final AdminOrderService adminOrderService;
    private final OperationLogService operationLogService;

    /** 用户列表 */
    @GetMapping("/users")
    public Result<PageResult<AdminUserVO>> users(
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(adminUserService.page(username, pageNum, pageSize));
    }

    /** 启用/禁用用户 */
    @PutMapping("/users/{id}/status")
    public Result<Void> updateUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminUserService.updateStatus(id, status);
        return Result.ok();
    }

    /** 平台订单列表 */
    @GetMapping("/orders")
    public Result<PageResult<OrderVO>> orders(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(adminOrderService.page(orderNo, status, pageNum, pageSize));
    }

    /** 平台订单详情 */
    @GetMapping("/orders/{id}")
    public Result<OrderDetailVO> orderDetail(@PathVariable Long id) {
        return Result.ok(adminOrderService.detail(id));
    }

    /** 操作日志 */
    @GetMapping("/operation-logs")
    public Result<PageResult<OperationLog>> logs(
            @RequestParam(required = false) String module,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(operationLogService.page(module, pageNum, pageSize));
    }
}
