package com.pandago.order.support;

import org.springframework.stereotype.Component;

/**
 * 超时关闭故障注入点（测试用，阶段 9）。
 *
 * <p>默认空操作，不影响生产。测试通过 Mockito 让某个步骤抛异常，
 * 验证"订单状态已条件更新但库存回补失败"时同一事务是否整体回滚，
 * 从而保证订单状态与库存始终一致。</p>
 */
@Component
public class OrderTimeoutFaultHook {

    /** 在回补库存之前调用：可模拟"部分回补后失败"以验证回滚。 */
    public void beforeRestock(Long orderId) {
    }
}
