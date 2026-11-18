package com.pandago.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.exception.BusinessException;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.order.service.OrderConfirmService;
import com.pandago.order.service.OrderConfirmTransactionService;
import com.pandago.order.support.OrderOperation;
import com.pandago.order.support.OrderStatusMachine;
import com.pandago.order.vo.OrderConfirmVO;
import com.pandago.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 确认收货校验层（无事务）：归属 + 状态机校验后，交事务服务写入。 */
@Service
@RequiredArgsConstructor
public class OrderConfirmServiceImpl implements OrderConfirmService {

    private final OrderMapper orderMapper;
    private final OrderConfirmTransactionService confirmTransactionService;

    @Override
    public OrderConfirmVO confirm(Long id) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getId, id)
                .eq(Orders::getUserId, userId));
        if (order == null) {
            // 他人订单与不存在订单统一 404
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在或不属于当前用户");
        }
        // 已完成的订单给出更具体的重复确认提示
        if (OrderStatus.COMPLETED.getValue().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "订单已确认收货，请勿重复操作");
        }
        OrderStatusMachine.assertTransition(
                order.getStatus(), OrderStatus.COMPLETED.getValue(), OrderOperation.CONFIRM_RECEIPT);
        return confirmTransactionService.confirm(order, userId);
    }
}
