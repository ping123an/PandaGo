package com.pandago.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.admin.service.OperationLogService;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.exception.BusinessException;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.order.service.OrderConfirmTransactionService;
import com.pandago.order.support.OrderOperation;
import com.pandago.order.support.OrderStatusMachine;
import com.pandago.order.vo.OrderConfirmVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 确认收货事务写入（阶段 8）。
 *
 * <p>条件 UPDATE 带 user_id + 源状态，只有本人且仍待收货的订单才变为已完成并写入 received_at。
 * 重复确认第二次影响 0 行，直接 409，没有任何写操作（幂等）。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderConfirmTransactionServiceImpl implements OrderConfirmTransactionService {

    private final OrderMapper orderMapper;
    private final OperationLogService operationLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderConfirmVO confirm(Orders submittedOrder, Long userId) {
        Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getId, submittedOrder.getId())
                .eq(Orders::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在或不属于当前用户");
        }
        OrderStatusMachine.assertTransition(
                order.getStatus(), OrderStatus.COMPLETED.getValue(), OrderOperation.CONFIRM_RECEIPT);

        LocalDateTime receivedAt = LocalDateTime.now();
        int rows = orderMapper.confirmReceipt(
                order.getId(), userId,
                OrderStatus.PENDING_RECEIPT.getValue(),
                OrderStatus.COMPLETED.getValue(), receivedAt);
        if (rows != 1) {
            throw new BusinessException(ResultCode.CONFLICT, "订单状态已变化，确认收货失败");
        }

        operationLogService.record("ORDER", "CONFIRM_RECEIPT", "ORDER", order.getId(), "用户确认收货");

        OrderConfirmVO vo = new OrderConfirmVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(OrderStatus.COMPLETED.getValue());
        vo.setStatusDescription(OrderStatus.describe(OrderStatus.COMPLETED.getValue()));
        vo.setReceivedAt(receivedAt);
        return vo;
    }
}
