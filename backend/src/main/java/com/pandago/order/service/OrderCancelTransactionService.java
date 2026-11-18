package com.pandago.order.service;

import com.pandago.order.entity.Orders;
import com.pandago.order.vo.OrderCancelVO;

/** 取消订单事务写入：状态条件更新与库存回补同事务。 */
public interface OrderCancelTransactionService {

    /**
     * @param order  已完成归属与状态校验的订单
     * @param userId 当前登录用户，来自 SecurityContext
     */
    OrderCancelVO cancel(Orders order, Long userId);
}
