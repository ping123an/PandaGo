package com.pandago;

import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.exception.BusinessException;
import com.pandago.order.support.OrderOperation;
import com.pandago.order.support.OrderStatusMachine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 阶段 10：核心「Service/领域逻辑」纯单元测试。
 *
 * <p>不加载 Spring 上下文、不连数据库，直接验证状态机这条"全项目唯一订单规则入口"。
 * 状态机是纯函数式组件，最适合用单元测试锁定其规则；集成行为由其它阶段测试覆盖。</p>
 */
class Stage10UnitTests {

    @Test
    @DisplayName("状态机：五态三参数流转与操作权限矩阵")
    void stateMachineOperationAndTransitionMatrix() {
        // 待支付：允许支付、用户取消、超时关闭
        assertEquals(Set.of(OrderOperation.PAY, OrderOperation.CANCEL_BY_USER, OrderOperation.TIMEOUT_CLOSE),
                OrderStatusMachine.allowedOperations(OrderStatus.PENDING_PAYMENT.getValue()));
        OrderStatusMachine.assertTransition("PENDING_PAYMENT", "PENDING_SHIPMENT", OrderOperation.PAY);
        OrderStatusMachine.assertTransition("PENDING_PAYMENT", "CANCELLED", OrderOperation.CANCEL_BY_USER);
        OrderStatusMachine.assertTransition("PENDING_PAYMENT", "CANCELLED", OrderOperation.TIMEOUT_CLOSE);

        // 待发货 -> 待收货 -> 已完成
        OrderStatusMachine.assertTransition("PENDING_SHIPMENT", "PENDING_RECEIPT", OrderOperation.SHIP);
        OrderStatusMachine.assertTransition("PENDING_RECEIPT", "COMPLETED", OrderOperation.CONFIRM_RECEIPT);

        // 终态无任何可用操作
        assertTrue(OrderStatusMachine.allowedOperations(OrderStatus.COMPLETED.getValue()).isEmpty());
        assertTrue(OrderStatusMachine.allowedOperations(OrderStatus.CANCELLED.getValue()).isEmpty());
    }

    @Test
    @DisplayName("状态机：拒绝非法操作（待发货不允许支付/取消）")
    void stateMachineRejectsIllegalOperation() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> OrderStatusMachine.assertOperation(OrderStatus.PENDING_SHIPMENT, OrderOperation.PAY));
        assertEquals(ResultCode.CONFLICT.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("不允许"));
    }

    @Test
    @DisplayName("状态机：拒绝非法流转（已完成不能回到待支付）")
    void stateMachineRejectsIllegalTransition() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> OrderStatusMachine.assertTransition(OrderStatus.COMPLETED, OrderStatus.PENDING_PAYMENT));
        assertEquals(ResultCode.CONFLICT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("状态机：非法状态字符串按数据异常处理（500 而非 409）")
    void stateMachineParseInvalidIsDataError() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> OrderStatusMachine.parse("NOT_A_STATUS"));
        assertEquals(ResultCode.INTERNAL_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("状态描述：非法值回退原串，null 回退空串")
    void describeFallback() {
        assertEquals("待支付", OrderStatus.describe("PENDING_PAYMENT"));
        assertEquals("UNKNOWN", OrderStatus.describe("UNKNOWN"));
        assertEquals("", OrderStatus.describe(null));
    }
}
