package com.pandago.payment.service;

import com.pandago.order.entity.Orders;
import com.pandago.payment.vo.PaymentVO;

/** 支付事务写入：支付记录与订单状态更新同事务。 */
public interface PaymentTransactionService {

    /**
     * 执行支付事务（默认模拟支付方式）。
     *
     * @param order  已完成归属校验的订单快照（方法内部会重新读取最新状态）
     * @param userId 当前登录用户，来自 SecurityContext
     */
    default PaymentVO pay(Orders order, Long userId) {
        return pay(order, userId, "MOCK");
    }

    /**
     * 执行支付事务。
     *
     * @param order         已完成归属校验的订单快照
     * @param userId        当前登录用户
     * @param paymentMethod 支付方式标记：ALIPAY/WECHAT/MOCK，仅作记录
     */
    PaymentVO pay(Orders order, Long userId, String paymentMethod);
}
