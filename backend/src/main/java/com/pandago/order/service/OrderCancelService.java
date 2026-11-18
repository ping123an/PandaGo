package com.pandago.order.service;

import com.pandago.order.vo.OrderCancelVO;

/** 用户主动取消订单入口（仅待支付订单允许）。 */
public interface OrderCancelService {

    /** 取消当前用户自己的待支付订单；重复取消返回 409 */
    OrderCancelVO cancel(Long orderId);
}
