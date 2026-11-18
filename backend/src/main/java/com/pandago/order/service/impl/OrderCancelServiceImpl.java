package com.pandago.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.exception.BusinessException;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.order.service.OrderCancelService;
import com.pandago.order.service.OrderCancelTransactionService;
import com.pandago.order.support.OrderOperation;
import com.pandago.order.support.OrderStatusMachine;
import com.pandago.order.vo.OrderCancelVO;
import com.pandago.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 取消订单校验层：身份、归属、状态机都在事务外完成。 */
@Service
@RequiredArgsConstructor
public class OrderCancelServiceImpl implements OrderCancelService {

    private final OrderMapper orderMapper;
    private final OrderCancelTransactionService cancelTransactionService;

    @Override
    public OrderCancelVO cancel(Long orderId) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getId, orderId)
                .eq(Orders::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在或不属于当前用户");
        }

        OrderStatusMachine.assertTransition(
                order.getStatus(), OrderStatus.CANCELLED.getValue(), OrderOperation.CANCEL_BY_USER);
        return cancelTransactionService.cancel(order, userId);
    }
}
