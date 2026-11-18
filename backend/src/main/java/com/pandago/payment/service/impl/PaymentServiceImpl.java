package com.pandago.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.exception.BusinessException;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.order.support.OrderOperation;
import com.pandago.order.support.OrderStatusMachine;
import com.pandago.payment.dto.MockPaymentDTO;
import com.pandago.payment.entity.PaymentRecord;
import com.pandago.payment.mapper.PaymentMapper;
import com.pandago.payment.service.PaymentService;
import com.pandago.payment.service.PaymentTransactionService;
import com.pandago.payment.vo.PaymentVO;
import com.pandago.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 模拟支付校验层（无事务）。
 *
 * <p>流程：SecurityContext 取用户 → 按订单号 + 用户查订单 → 状态机校验 → 调用独立事务服务。
 * 所有金额都来自 orders.total_amount；DTO 不声明金额字段，前端夹带的价格不会被读取。</p>
 *
 * <p>并发重复支付时，事务服务内的 payment_record 唯一索引会抛 DuplicateKeyException；
 * 这里把它转成明确的 409 业务结果，并回查原支付记录带出流水号。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentTransactionService paymentTransactionService;

    @Override
    public PaymentVO mockPay(MockPaymentDTO dto) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        Orders order = requireOwnOrderByNo(dto.getOrderNo(), userId);

        // 已支付订单给出更具体的重复支付提示（状态机仍会统一拒绝该操作）
        if (OrderStatus.PENDING_SHIPMENT.getValue().equals(order.getStatus())) {
            PaymentRecord paid = paymentMapper.selectByOrderNoAndUser(dto.getOrderNo(), userId);
            if (paid != null) {
                throw new BusinessException(ResultCode.CONFLICT,
                        "订单已支付成功，请勿重复支付。支付流水号：" + paid.getPaymentNo());
            }
        }

        // 状态机先拒绝已取消、已支付及未来其他非法状态；并发下的状态竞态由条件 UPDATE 兜底
        OrderStatusMachine.assertTransition(
                order.getStatus(), OrderStatus.PENDING_SHIPMENT.getValue(), OrderOperation.PAY);

        try {
            return paymentTransactionService.pay(order, userId, dto.getPaymentMethod());
        } catch (DuplicateKeyException ex) {
            // 唯一索引命中：要么同一订单已支付，要么极小概率流水号重复
            PaymentRecord existing = paymentMapper.selectByOrderNoAndUser(dto.getOrderNo(), userId);
            if (existing != null) {
                throw new BusinessException(ResultCode.CONFLICT,
                        "订单已支付成功，请勿重复支付。支付流水号：" + existing.getPaymentNo());
            }
            log.warn("支付唯一键冲突但未找到已有支付记录, orderNo={}", dto.getOrderNo());
            throw new BusinessException(ResultCode.CONFLICT, "支付请求冲突，请稍后查询支付结果后重试");
        }
    }

    @Override
    public PaymentVO resultByOrderNo(String orderNo) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        Orders order = requireOwnOrderByNo(orderNo, userId);
        PaymentRecord payment = paymentMapper.selectByOrderNoAndUser(orderNo, userId);
        return toVO(payment, order);
    }

    @Override
    public PaymentVO resultByOrderId(Long orderId) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        Orders order = requireOwnOrderById(orderId, userId);
        PaymentRecord payment = paymentMapper.selectByOrderNoAndUser(order.getOrderNo(), userId);
        return toVO(payment, order);
    }

    @Override
    public PaymentVO mockPayByOrderId(Long orderId, String paymentMethod) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        Orders order = requireOwnOrderById(orderId, userId);
        MockPaymentDTO dto = new MockPaymentDTO();
        dto.setOrderNo(order.getOrderNo());
        dto.setPaymentMethod(paymentMethod);
        return mockPay(dto);
    }

    private Orders requireOwnOrderById(Long orderId, Long userId) {
        Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getId, orderId)
                .eq(Orders::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在或不属于当前用户");
        }
        return order;
    }

    private Orders requireOwnOrderByNo(String orderNo, Long userId) {
        Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getOrderNo, orderNo)
                .eq(Orders::getUserId, userId));
        if (order == null) {
            // 他人订单与不存在订单统一 404，避免探测
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在或不属于当前用户");
        }
        return order;
    }

    /**
     * 支付记录与订单状态装配。未支付时 id/paymentNo/paidAt 为 null，paid=false。
     */
    private PaymentVO toVO(PaymentRecord payment, Orders order) {
        PaymentVO vo = new PaymentVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setOrderStatus(order.getStatus());
        vo.setOrderStatusDescription(OrderStatus.describe(order.getStatus()));
        if (payment == null) {
            vo.setPayAmount(order.getTotalAmount());
            vo.setPaymentMethod(PaymentRecord.METHOD_MOCK);
            vo.setStatus("UNPAID");
            vo.setPaid(false);
            vo.setCreatedAt(order.getCreatedAt());
            return vo;
        }
        vo.setId(payment.getId());
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setPayAmount(payment.getPayAmount());
        vo.setPaymentMethod(payment.getPaymentMethod());
        vo.setStatus(payment.getStatus());
        vo.setPaidAt(payment.getPaidAt());
        vo.setPaid(PaymentRecord.STATUS_SUCCESS.equals(payment.getStatus()));
        vo.setCreatedAt(order.getCreatedAt());
        return vo;
    }
}
