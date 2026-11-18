package com.pandago.order.service;

/** 超时订单扫描协调（阶段 9）：分批查询 + 逐单关闭 + 汇总统计。 */
public interface OrderTimeoutScanService {

    /**
     * 扫描并关闭一批超时订单。
     *
     * @return 本次成功关闭的订单数（未超时/已支付/并发抢失败的订单不计入）
     */
    int scanAndClose();
}
