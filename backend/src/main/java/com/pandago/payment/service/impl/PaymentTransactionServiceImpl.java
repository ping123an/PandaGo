package com.pandago.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.exception.BusinessException;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.order.support.OrderOperation;
import com.pandago.order.support.OrderStatusMachine;
import com.pandago.payment.entity.PaymentRecord;
import com.pandago.payment.mapper.PaymentMapper;
import com.pandago.payment.service.PaymentTransactionService;
import com.pandago.payment.support.PaymentFaultHook;
import com.pandago.payment.support.PaymentNoGenerator;
import com.pandago.payment.vo.PaymentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 模拟支付事务写入。
 *
 * <p>事务边界在本 Bean 的 pay() 上，调用方 PaymentServiceImpl 无事务，
 * 通过 Spring 代理进入这里，@Transactional 必定生效。</p>
 *
 * <p>事务内顺序：重新读取最新订单 → 状态机再次校验 → 写支付记录 → 条件 UPDATE 订单状态。
 * 任一失败（含故障注入）整体回滚，不会出现"有支付记录但订单仍待支付"。</p>
 */
@Service
@RequiredArgsConstructor
public class PaymentTransactionServiceImpl implements PaymentTransactionService {

    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentNoGenerator paymentNoGenerator;
    private final PaymentFaultHook faultHook;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentVO pay(Orders submittedOrder, Long userId, String paymentMethod) {
        // 在事务内重新读取，避免校验后订单被取消等竞态
        Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                .eq(Orders::getId, submittedOrder.getId())
                .eq(Orders::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在或不属于当前用户");
        }
        OrderStatusMachine.assertTransition(
                order.getStatus(), OrderStatus.PENDING_SHIPMENT.getValue(), OrderOperation.PAY);

        // 金额只取订单快照；请求里的 amount/payAmount 根本没有进入 DTO
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentNo(paymentNoGenerator.next());
        payment.setOrderId(order.getId());
        payment.setOrderNo(order.getOrderNo());
        payment.setUserId(userId);
        payment.setPayAmount(order.getTotalAmount());
        String method = normalizeMethod(paymentMethod);
        payment.setPaymentMethod(method);
        payment.setStatus(PaymentRecord.STATUS_SUCCESS);
        payment.setPaidAt(LocalDateTime.now());

        if (paymentMapper.insert(payment) != 1) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "支付记录写入失败");
        }
        // 教学测试接缝：此处抛异常应回滚已写入的支付记录
        faultHook.afterPaymentInsert();

        // 条件更新：只有仍是 PENDING_PAYMENT 的本人订单才能变更为 PENDING_SHIPMENT
        int rows = orderMapper.transitionStatus(
                order.getId(), userId,
                OrderStatus.PENDING_PAYMENT.getValue(),
                OrderStatus.PENDING_SHIPMENT.getValue());
        if (rows != 1) {
            throw new BusinessException(ResultCode.CONFLICT, "订单状态已变化，支付未完成，请查询支付结果");
        }

        PaymentVO vo = new PaymentVO();
        vo.setId(payment.getId());
        vo.setPaymentNo(payment.getPaymentNo());
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setPayAmount(payment.getPayAmount());
        vo.setPaymentMethod(payment.getPaymentMethod());
        vo.setStatus(payment.getStatus());
        vo.setPaidAt(payment.getPaidAt());
        vo.setPaid(true);
        vo.setOrderStatus(OrderStatus.PENDING_SHIPMENT.getValue());
        vo.setOrderStatusDescription(OrderStatus.describe(OrderStatus.PENDING_SHIPMENT.getValue()));
        vo.setCreatedAt(order.getCreatedAt());
        return vo;
    }

    /** 支付方式白名单：非法值回退 MOCK，避免前端夹带任意标记污染支付记录 */
    private String normalizeMethod(String method) {
        if (PaymentRecord.METHOD_ALIPAY.equals(method) || PaymentRecord.METHOD_WECHAT.equals(method)) {
            return method;
        }
        return PaymentRecord.METHOD_MOCK;
    }
}
