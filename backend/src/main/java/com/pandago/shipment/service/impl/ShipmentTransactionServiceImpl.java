package com.pandago.shipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.admin.service.OperationLogService;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.exception.BusinessException;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.order.support.OrderOperation;
import com.pandago.order.support.OrderStatusMachine;
import com.pandago.shipment.dto.ShipmentDTO;
import com.pandago.shipment.entity.Shipment;
import com.pandago.shipment.mapper.ShipmentMapper;
import com.pandago.shipment.service.ShipmentTransactionService;
import com.pandago.shipment.vo.ShipmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 商家发货事务写入（阶段 8）。
 *
 * <p>事务边界在本 Bean：从校验层经 Spring 代理进入，@Transactional 必定生效。
 * 顺序：事务内重读订单 + 状态机复核 → 条件 UPDATE 抢状态变更权 → 写物流记录 → 写操作日志。
 * 只有条件 UPDATE 影响 1 行才说明本请求赢得了发货权；重复/并发发货第二次影响 0 行，直接 409，
 * 不会产生第二条物流记录（uk_shipment_order 也是最后一道兜底）。</p>
 */
@Service
@RequiredArgsConstructor
public class ShipmentTransactionServiceImpl implements ShipmentTransactionService {

    private final OrderMapper orderMapper;
    private final ShipmentMapper shipmentMapper;
    private final OperationLogService operationLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShipmentVO ship(Orders submittedOrder, Long merchantId, ShipmentDTO dto) {
        // 事务内重读，避免"校验后订单状态被并发改动"
        Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getId, submittedOrder.getId())
                .eq(Orders::getMerchantId, merchantId));
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在或不属于当前商家");
        }
        OrderStatusMachine.assertTransition(
                order.getStatus(), OrderStatus.PENDING_RECEIPT.getValue(), OrderOperation.SHIP);

        int rows = orderMapper.shipOrder(
                order.getId(), merchantId,
                OrderStatus.PENDING_SHIPMENT.getValue(),
                OrderStatus.PENDING_RECEIPT.getValue());
        if (rows != 1) {
            throw new BusinessException(ResultCode.CONFLICT, "订单状态已变化，发货失败");
        }

        // 只有拿到状态变更权才写物流；唯一索引保证一单一条物流
        Shipment shipment = new Shipment();
        shipment.setOrderId(order.getId());
        shipment.setOrderNo(order.getOrderNo());
        shipment.setMerchantId(merchantId);
        shipment.setCompany(dto.getCompany().trim());
        shipment.setTrackingNo(dto.getTrackingNo().trim());
        shipment.setShippedAt(LocalDateTime.now());
        shipmentMapper.insert(shipment);

        operationLogService.record("ORDER", "SHIP", "ORDER", order.getId(),
                "订单发货，物流公司=" + shipment.getCompany() + "，单号=" + shipment.getTrackingNo());

        ShipmentVO vo = new ShipmentVO();
        vo.setId(shipment.getId());
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setCompany(shipment.getCompany());
        vo.setTrackingNo(shipment.getTrackingNo());
        vo.setShippedAt(shipment.getShippedAt());
        return vo;
    }
}
