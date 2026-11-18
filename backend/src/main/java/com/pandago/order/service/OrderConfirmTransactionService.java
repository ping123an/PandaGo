package com.pandago.order.service;

import com.pandago.order.entity.Orders;
import com.pandago.order.vo.OrderConfirmVO;

/** 确认收货事务入口（阶段 8）：条件状态更新 + 收货时间同事务。 */
public interface OrderConfirmTransactionService {

    OrderConfirmVO confirm(Orders submittedOrder, Long userId);
}
