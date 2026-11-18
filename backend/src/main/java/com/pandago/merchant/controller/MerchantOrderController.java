package com.pandago.merchant.controller;

import com.pandago.common.api.PageResult;
import com.pandago.common.api.Result;
import com.pandago.merchant.service.MerchantOrderService;
import com.pandago.order.vo.OrderDetailVO;
import com.pandago.order.vo.OrderVO;
import com.pandago.shipment.dto.ShipmentDTO;
import com.pandago.shipment.vo.ShipmentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商家订单接口（阶段 8）。
 *
 * <p>URL 规则（/merchant/**）与方法级 @PreAuthorize 先挡住普通用户，
 * Service 再校验商家角色与订单归属。商家 id 只从 SecurityContext 读取。</p>
 */
@Validated
@PreAuthorize("hasRole('MERCHANT')")
@RestController
@RequestMapping("/api/v1/merchant/orders")
@RequiredArgsConstructor
public class MerchantOrderController {

    private final MerchantOrderService merchantOrderService;

    /** 我的商家订单分页，可按状态筛选 */
    @GetMapping
    public Result<PageResult<OrderVO>> page(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(merchantOrderService.page(status, pageNum, pageSize));
    }

    /** 本商家订单详情 */
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(merchantOrderService.detail(id));
    }

    /** 发货：只接收物流公司与单号，状态由后端判定 */
    @PostMapping("/{id}/ship")
    public Result<ShipmentVO> ship(@PathVariable Long id, @Valid @RequestBody ShipmentDTO dto) {
        return Result.ok(merchantOrderService.ship(id, dto));
    }

    /** 本商家订单物流信息 */
    @GetMapping("/{id}/shipment")
    public Result<ShipmentVO> shipment(@PathVariable Long id) {
        return Result.ok(merchantOrderService.shipment(id));
    }
}
