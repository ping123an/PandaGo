package com.pandago.order.service;

import com.pandago.common.api.PageResult;
import com.pandago.order.dto.OrderCreateDTO;
import com.pandago.order.dto.OrderPreviewDTO;
import com.pandago.order.dto.OrderQueryDTO;
import com.pandago.order.vo.OrderCreateVO;
import com.pandago.order.vo.OrderPreviewVO;
import com.pandago.order.vo.OrderDetailVO;
import com.pandago.order.vo.OrderStatusVO;
import com.pandago.order.vo.OrderVO;

/** 订单查询与创建入口（校验在 Service，事务写入委托 OrderTransactionService）。 */
public interface OrderService {

    /** 校验并创建订单；事务边界在内部调用的 OrderTransactionService.commit 上 */
    OrderCreateVO create(OrderCreateDTO dto);

    /** 订单预览：仅查询与计算，不写库；价格/商家/库存来自数据库，供确认订单页展示 */
    OrderPreviewVO preview(OrderPreviewDTO dto);

    /** 当前用户订单分页 */
    PageResult<OrderVO> page(OrderQueryDTO query, long pageNum, long pageSize);

    /** 当前用户订单详情；非本人订单抛 404 */
    OrderDetailVO detail(Long id);

    /** 轻量订单状态查询：状态描述 + 后端状态机允许的后续操作；非本人订单抛 404 */
    OrderStatusVO status(Long id);
}
