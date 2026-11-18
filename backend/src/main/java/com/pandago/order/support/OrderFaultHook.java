package com.pandago.order.support;

import org.springframework.stereotype.Component;

/**
 * 订单写入故障注入点（教学与测试用）。
 *
 * <p>默认实现为空操作，不影响生产逻辑。测试通过 Mockito 让某个步骤抛异常，
 * 从而精确验证"订单主表写入失败 / 明细写入失败 / 扣库存后失败"时事务是否整体回滚。
 * 这是一个显式的测试接缝：它不参与业务判断，只在被测试替换时才生效。</p>
 */
@Component
public class OrderFaultHook {

    public void afterStockDeduct() {
    }

    public void beforeOrderInsert() {
    }

    public void beforeOrderItemInsert() {
    }
}
