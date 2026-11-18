package com.pandago.payment.support;

import org.springframework.stereotype.Component;

/**
 * 支付事务故障注入点。
 *
 * <p>默认空实现，仅用于测试模拟"支付记录写入后、订单状态更新前"失败，
 * 以验证支付记录与订单状态处于同一事务。</p>
 */
@Component
public class PaymentFaultHook {

    public void afterPaymentInsert() {
        // 默认无操作
    }
}
