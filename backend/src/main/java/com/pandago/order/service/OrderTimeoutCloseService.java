package com.pandago.order.service;

import java.time.LocalDateTime;

/** 单笔超时订单关闭的事务写入（阶段 9）。 */
public interface OrderTimeoutCloseService {

    /**
     * 关闭单笔超时订单：条件状态更新成功后才回补库存，二者共享同一事务。
     *
     * @param orderId 订单 ID
     * @param cutoff  超时截止时间（created_at &lt;= cutoff 才算超时）
     * @return true 表示本次确实关闭并回补；false 表示订单已被支付/取消/未超时，跳过
     */
    boolean closeOne(Long orderId, LocalDateTime cutoff);
}
