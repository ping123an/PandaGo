package com.pandago.order.support;

import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.exception.BusinessException;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态机（阶段 7）。
 *
 * <p>全项目唯一的订单状态规则入口：每种状态允许哪些操作、每条流转是否合法都在这里定义。
 * 支付、用户取消、未来的商家发货/用户确认收货/超时关闭都必须先调用这里，
 * 不允许 Controller 或 Service 直接给订单 setStatus。</p>
 *
 * <p>状态机只负责"应不应该做"；"能不能并发安全地做成"仍由条件 UPDATE 和唯一索引保证。
 * 两者缺一不可：状态机防止非法意图，数据库约束防止并发竞态。</p>
 */
public final class OrderStatusMachine {

    private static final Map<OrderStatus, EnumSet<OrderOperation>> ALLOWED_OPERATIONS = Map.of(
            OrderStatus.PENDING_PAYMENT,
                    EnumSet.of(OrderOperation.PAY, OrderOperation.CANCEL_BY_USER, OrderOperation.TIMEOUT_CLOSE),
            OrderStatus.PENDING_SHIPMENT, EnumSet.of(OrderOperation.SHIP),
            OrderStatus.PENDING_RECEIPT, EnumSet.of(OrderOperation.CONFIRM_RECEIPT),
            OrderStatus.COMPLETED, EnumSet.noneOf(OrderOperation.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderOperation.class)
    );

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING_PAYMENT, Set.of(OrderStatus.PENDING_SHIPMENT, OrderStatus.CANCELLED),
            OrderStatus.PENDING_SHIPMENT, Set.of(OrderStatus.PENDING_RECEIPT),
            OrderStatus.PENDING_RECEIPT, Set.of(OrderStatus.COMPLETED),
            OrderStatus.COMPLETED, Set.of(),
            OrderStatus.CANCELLED, Set.of()
    );

    private OrderStatusMachine() {
    }

    /** 解析数据库状态字符串；非法值属于数据异常，不把它当成普通业务冲突。 */
    public static OrderStatus parse(String value) {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        throw new BusinessException(ResultCode.INTERNAL_ERROR, "订单状态数据异常");
    }

    /** 校验当前状态是否允许执行指定操作。 */
    public static void assertOperation(String currentStatus, OrderOperation operation) {
        assertOperation(parse(currentStatus), operation);
    }

    /** 校验当前状态是否允许执行指定操作。 */
    public static void assertOperation(OrderStatus currentStatus, OrderOperation operation) {
        Set<OrderOperation> allowed = ALLOWED_OPERATIONS.getOrDefault(
                currentStatus, EnumSet.noneOf(OrderOperation.class));
        if (!allowed.contains(operation)) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "订单当前状态「" + currentStatus.getDescription() + "」不允许执行「"
                            + operation.getDescription() + "」操作");
        }
    }

    /** 校验从 from 到 to 的流转是否合法。 */
    public static void assertTransition(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> targets = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!targets.contains(to)) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "订单不允许从「" + from.getDescription() + "」变更为「" + to.getDescription() + "」");
        }
    }

    /** 一次校验操作权限与目标状态，支付/取消等写操作统一调用此方法。 */
    public static void assertTransition(String fromStatus, String toStatus, OrderOperation operation) {
        OrderStatus from = parse(fromStatus);
        assertOperation(from, operation);
        assertTransition(from, parse(toStatus));
    }

    /** 供状态查询接口返回可用操作，前端据此决定显示哪些按钮。 */
    public static Set<OrderOperation> allowedOperations(String status) {
        Set<OrderOperation> operations = ALLOWED_OPERATIONS.getOrDefault(
                parse(status), EnumSet.noneOf(OrderOperation.class));
        return Collections.unmodifiableSet(EnumSet.copyOf(operations));
    }
}
