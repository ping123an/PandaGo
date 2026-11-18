package com.pandago.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.exception.BusinessException;
import com.pandago.order.entity.OrderItem;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderItemMapper;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.order.service.OrderTimeoutCloseService;
import com.pandago.order.support.OrderTimeoutFaultHook;
import com.pandago.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时订单关闭事务实现（阶段 9）。
 *
 * <p>与阶段 7 用户取消同构，但不带 userId（定时任务无登录身份），改用
 * "状态机目标 + created_at 截止时间"作为条件：
 * 1. {@link OrderMapper#closeTimeoutOrder} 条件 UPDATE，只有仍待支付且已超时的订单影响 1 行；
 * 2. 只有拿到变更权（rows==1）才回补库存，重复扫描第二次 rows==0 直接跳过；
 * 3. 状态更新与全部库存回补在同一事务，任一回补失败整体回滚，订单状态不会与库存脱节。</p>
 *
 * <p>这里不校验 OrderStatusMachine：定时任务的语义就是"待支付 → 超时取消"这一条固定合法流转，
 * 条件 UPDATE 的 from 状态已等同于状态机断言。复用阶段 6/7 已验证的回补 SQL，不改写其逻辑。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderTimeoutCloseServiceImpl implements OrderTimeoutCloseService {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutCloseServiceImpl.class);

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;
    private final OrderTimeoutFaultHook faultHook;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean closeOne(Long orderId, LocalDateTime cutoff) {
        int rows = orderMapper.closeTimeoutOrder(
                orderId,
                OrderStatus.PENDING_PAYMENT.getValue(),
                OrderStatus.CANCELLED.getValue(),
                cutoff);
        if (rows == 0) {
            // 已被支付/已被用户取消/未超时：没有拿到变更权，绝不回补库存
            return false;
        }
        if (rows != 1) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "超时关闭影响行数异常");
        }

        faultHook.beforeRestock(orderId);

        List<OrderItem> items = orderItemMapper.selectList(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId)
                .orderByAsc(OrderItem::getId));
        for (OrderItem item : items) {
            int restored = productMapper.restoreStock(item.getProductId(), item.getQuantity());
            if (restored != 1) {
                // 抛异常触发整个事务回滚：订单状态回到 PENDING_PAYMENT，库存也不回补
                throw new BusinessException(ResultCode.INTERNAL_ERROR, "超时关闭库存回补失败");
            }
        }
        log.info("超时订单已关闭: orderId={}, items={}", orderId, items.size());
        return true;
    }
}
