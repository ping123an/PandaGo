package com.pandago.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pandago.common.api.PageResult;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.common.exception.BusinessException;
import com.pandago.merchant.service.MerchantOrderService;
import com.pandago.order.entity.OrderItem;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderItemMapper;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.order.support.OrderOperation;
import com.pandago.order.support.OrderStatusMachine;
import com.pandago.order.vo.OrderDetailVO;
import com.pandago.order.vo.OrderItemVO;
import com.pandago.order.vo.OrderVO;
import com.pandago.security.SecurityUtils;
import com.pandago.shipment.dto.ShipmentDTO;
import com.pandago.shipment.entity.Shipment;
import com.pandago.shipment.mapper.ShipmentMapper;
import com.pandago.shipment.service.ShipmentTransactionService;
import com.pandago.shipment.vo.ShipmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家订单业务实现（阶段 8）。
 *
 * <p>安全要点：所有方法先经 {@link #currentMerchantId()} 做 MERCHANT 角色校验并从
 * SecurityContext 读取商家 id，查询与写操作都把它作为强制条件。前端传来的 merchantId、
 * userId、status 目标值一律不作为数据归属依据（status 只作为筛选条件）。</p>
 *
 * <p>状态变更不在这里直接 setStatus：发货先经状态机校验意图，再由事务服务用条件 UPDATE
 * 抢变更权并写物流，与阶段 7 支付/取消保持同一套模型。</p>
 */
@Service
@RequiredArgsConstructor
public class MerchantOrderServiceImpl implements MerchantOrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ShipmentMapper shipmentMapper;
    private final ShipmentTransactionService shipmentTransactionService;

    @Override
    public PageResult<OrderVO> page(String status, long pageNum, long pageSize) {
        Long merchantId = currentMerchantId();
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<Orders>()
                .eq(Orders::getMerchantId, merchantId);
        if (StringUtils.hasText(status)) {
            // 状态只作为筛选条件，非法值查不到数据，不影响归属
            wrapper.eq(Orders::getStatus, status);
        }
        wrapper.orderByDesc(Orders::getCreatedAt);

        IPage<Orders> result = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<Orders> orders = result.getRecords();

        // 批量装配明细，避免逐单查询 N+1
        Map<Long, List<OrderItem>> itemMap = new HashMap<>();
        if (!orders.isEmpty()) {
            List<Long> orderIds = orders.stream().map(Orders::getId).toList();
            for (OrderItem item : orderItemMapper.selectByOrderIds(orderIds)) {
                itemMap.computeIfAbsent(item.getOrderId(), k -> new ArrayList<>()).add(item);
            }
        }
        List<OrderVO> vos = orders.stream().map(order -> {
            List<OrderItemVO> items = itemMap.getOrDefault(order.getId(), List.of()).stream()
                    .map(this::toItemVO).toList();
            OrderVO vo = new OrderVO();
            vo.setId(order.getId());
            vo.setOrderNo(order.getOrderNo());
            vo.setTotalAmount(order.getTotalAmount());
            vo.setStatus(order.getStatus());
            vo.setStatusDescription(OrderStatus.describe(order.getStatus()));
            vo.setRemark(order.getRemark());
            vo.setCreatedAt(order.getCreatedAt());
            vo.setItems(items);
            vo.setItemCount(items.size());
            if (!items.isEmpty()) {
                vo.setFirstProductName(items.get(0).getProductName());
                vo.setFirstProductImage(items.get(0).getProductImage());
            }
            return vo;
        }).toList();
        return PageResult.of(vos, result.getTotal(), pageNum, pageSize, result.getPages());
    }

    @Override
    public OrderDetailVO detail(Long id) {
        Orders order = requireOwnedOrder(id);
        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId())
                .orderByAsc(OrderItem::getId));

        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusDescription(OrderStatus.describe(order.getStatus()));
        vo.setRemark(order.getRemark());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverProvince(order.getReceiverProvince());
        vo.setReceiverCity(order.getReceiverCity());
        vo.setReceiverDistrict(order.getReceiverDistrict());
        vo.setReceiverDetailAddress(order.getReceiverDetailAddress());
        vo.setItems(items.stream().map(this::toItemVO).toList());
        return vo;
    }

    @Override
    public ShipmentVO ship(Long id, ShipmentDTO dto) {
        Orders order = requireOwnedOrder(id);
        // 状态机先校验"应不应该发货"；并发竞态由事务服务里的条件 UPDATE 兜底
        OrderStatusMachine.assertTransition(
                order.getStatus(), OrderStatus.PENDING_RECEIPT.getValue(), OrderOperation.SHIP);
        return shipmentTransactionService.ship(order, order.getMerchantId(), dto);
    }

    @Override
    public ShipmentVO shipment(Long id) {
        Orders order = requireOwnedOrder(id);
        Shipment shipment = shipmentMapper.selectOne(new LambdaQueryWrapper<Shipment>()
                .eq(Shipment::getOrderId, order.getId()));
        if (shipment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "该订单暂无物流信息");
        }
        return toVO(shipment);
    }

    /** MERCHANT 角色 + 当前商家 id，任何归属查询前先调用 */
    private Long currentMerchantId() {
        SecurityUtils.requireRole(RoleCode.MERCHANT);
        return SecurityUtils.requireCurrentUser().getUserId();
    }

    /** 归属校验：找不到 404，非本人商家 403 */
    private Orders requireOwnedOrder(Long id) {
        Long merchantId = currentMerchantId();
        Orders order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        if (order.getMerchantId() == null || !merchantId.equals(order.getMerchantId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己商家的订单");
        }
        return order;
    }

    private OrderItemVO toItemVO(OrderItem item) {
        OrderItemVO vo = new OrderItemVO();
        vo.setId(item.getId());
        vo.setProductId(item.getProductId());
        vo.setProductName(item.getProductName());
        vo.setProductImage(item.getProductImage());
        vo.setUnitPrice(item.getUnitPrice());
        vo.setQuantity(item.getQuantity());
        vo.setSubtotal(item.getSubtotal());
        return vo;
    }

    private ShipmentVO toVO(Shipment shipment) {
        ShipmentVO vo = new ShipmentVO();
        vo.setId(shipment.getId());
        vo.setOrderId(shipment.getOrderId());
        vo.setOrderNo(shipment.getOrderNo());
        vo.setCompany(shipment.getCompany());
        vo.setTrackingNo(shipment.getTrackingNo());
        vo.setShippedAt(shipment.getShippedAt());
        return vo;
    }
}
