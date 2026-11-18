package com.pandago.admin.service;

import com.pandago.common.api.PageResult;
import com.pandago.order.vo.OrderDetailVO;
import com.pandago.order.vo.OrderVO;

/** 管理端平台订单查询（阶段 8）：只读，不绕过状态机改状态。 */
public interface AdminOrderService {

    /** 全平台订单分页：可按订单号、状态筛选 */
    PageResult<OrderVO> page(String orderNo, String status, long pageNum, long pageSize);

    /** 全平台订单详情 */
    OrderDetailVO detail(Long id);
}
