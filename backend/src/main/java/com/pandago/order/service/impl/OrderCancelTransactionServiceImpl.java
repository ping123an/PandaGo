package com.pandago.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.exception.BusinessException;
import com.pandago.order.entity.OrderItem;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderItemMapper;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.order.service.OrderCancelTransactionService;
import com.pandago.order.vo.OrderCancelVO;
import com.pandago.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 取消订单事务写入。
 *
 * <p>先做条件状态更新，只有成功从 PENDING_PAYMENT 变为 CANCELLED 才回补库存。
 * 并发取消时第二个请求的 UPDATE 影响 0 行，直接返回 409，不会再次回补库存。</p>
 *
 * <p>状态更新与所有库存回补共享一个事务：任一商品回补失败，订单状态也随之回滚。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderCancelTransactionServiceImpl implements OrderCancelTransactionService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderCancelVO cancel(Orders order, Long userId) {
        int rows = orderMapper.transitionStatus(
                order.getId(), userId,
                OrderStatus.PENDING_PAYMENT.getValue(),
                OrderStatus.CANCELLED.getValue());
        if (rows != 1) {
            // 并发取消 / 已被支付：没有获得状态变更权，绝不能回补库存
            throw new BusinessException(ResultCode.CONFLICT, "订单状态已变化，取消失败");
        }

        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, order.getId())
                .orderByAsc(OrderItem::getId));
        for (OrderItem item : items) {
            int restored = productMapper.restoreStock(item.getProductId(), item.getQuantity());
            if (restored != 1) {
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "库存回补失败");
            }
        }

        OrderCancelVO vo = new OrderCancelVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setStatus(OrderStatus.CANCELLED.getValue());
        vo.setStatusDescription(OrderStatus.describe(OrderStatus.CANCELLED.getValue()));
        return vo;
    }
}
