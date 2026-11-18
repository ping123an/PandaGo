package com.pandago.order.service;

import com.pandago.order.support.OrderDraft;
import com.pandago.order.vo.OrderCreateVO;

import java.util.List;

/** 订单创建的事务性写服务：与校验逻辑分离，保证 @Transactional 通过代理生效。 */
public interface OrderTransactionService {

    /**
     * 在单一事务内完成所有商家的：条件扣库存 → 订单主表 → 订单明细 → 清理购物车。
     * 多商家拆单时 drafts 含多张草稿，全部在同一事务内；任一步抛异常，所有写操作整体回滚。
     * checkout_group_id + merchant_id 唯一索引保证同一结算批次同一商家不会重复建单。
     */
    OrderCreateVO commit(List<OrderDraft> drafts);
}
