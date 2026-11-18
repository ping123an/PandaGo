package com.pandago.order.controller;

import com.pandago.common.api.PageResult;
import com.pandago.common.api.Result;
import com.pandago.order.dto.OrderCreateDTO;
import com.pandago.order.dto.OrderPreviewDTO;
import com.pandago.order.dto.OrderQueryDTO;
import com.pandago.order.service.OrderCancelService;
import com.pandago.order.service.OrderConfirmService;
import com.pandago.order.service.OrderService;
import com.pandago.order.vo.OrderCancelVO;
import com.pandago.order.vo.OrderConfirmVO;
import com.pandago.order.vo.OrderCreateVO;
import com.pandago.order.vo.OrderDetailVO;
import com.pandago.order.vo.OrderPreviewVO;
import com.pandago.order.vo.OrderStatusVO;
import com.pandago.order.vo.OrderVO;
import com.pandago.shipment.service.ShipmentQueryService;
import com.pandago.shipment.vo.ShipmentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户订单接口（阶段 6）。
 *
 * <p>只接收必要信息：购物车条目 ID 或商品 ID+数量、地址 ID、备注。
 * 不接收价格、金额、userId、merchantId；身份由 SecurityContext 决定。</p>
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService orderService;
    private final OrderCancelService orderCancelService;
    private final OrderConfirmService orderConfirmService;
    private final ShipmentQueryService shipmentQueryService;

    /** 创建订单（事务性） */
    @PostMapping
    public Result<OrderCreateVO> create(@Valid @RequestBody OrderCreateDTO dto) {
        return Result.ok(orderService.create(dto));
    }

    /** 订单预览：只查库计算，不写库；立即购买与购物车结算共用 */
    @PostMapping("/preview")
    public Result<OrderPreviewVO> preview(@Valid @RequestBody OrderPreviewDTO dto) {
        return Result.ok(orderService.preview(dto));
    }

    /** 我的订单分页，可按状态筛选 */
    @GetMapping
    public Result<PageResult<OrderVO>> page(@Valid OrderQueryDTO query,
                                            @RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(orderService.page(query, pageNum, pageSize));
    }

    /** 轻量状态查询：当前状态 + 后端状态机允许的后续操作 */
    @GetMapping("/{id}/status")
    public Result<OrderStatusVO> status(@PathVariable Long id) {
        return Result.ok(orderService.status(id));
    }

    /** 我的订单详情：从快照读取，非本人返回 404 */
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id));
    }

    /** 用户取消待支付订单：状态条件更新成功后才回补库存 */
    @PostMapping("/{id}/cancel")
    public Result<OrderCancelVO> cancel(@PathVariable Long id) {
        return Result.ok(orderCancelService.cancel(id));
    }

    /** 用户确认收货：仅本人待收货订单，重复确认返回 409 */
    @PostMapping("/{id}/confirm")
    public Result<OrderConfirmVO> confirm(@PathVariable Long id) {
        return Result.ok(orderConfirmService.confirm(id));
    }

    /** 用户查看自己订单的物流信息：只有公司与单号，无轨迹 */
    @GetMapping("/{id}/shipment")
    public Result<ShipmentVO> shipment(@PathVariable Long id) {
        return Result.ok(shipmentQueryService.mineByOrderId(id));
    }
}
