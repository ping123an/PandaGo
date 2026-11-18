package com.pandago.order.service;

import com.pandago.order.vo.OrderConfirmVO;

/** 用户确认收货（阶段 8）。 */
public interface OrderConfirmService {

    /** 确认自己名下待收货订单；重复确认返回 409，无副作用 */
    OrderConfirmVO confirm(Long id);
}
