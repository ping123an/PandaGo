package com.pandago.merchant.service;

import com.pandago.common.api.PageResult;
import com.pandago.order.vo.OrderDetailVO;
import com.pandago.order.vo.OrderVO;
import com.pandago.shipment.dto.ShipmentDTO;
import com.pandago.shipment.vo.ShipmentVO;

/** 商家订单业务（阶段 8）：查询本商家订单、发货、查看物流。 */
public interface MerchantOrderService {

    /** 我的商家订单分页，可按状态筛选 */
    PageResult<OrderVO> page(String status, long pageNum, long pageSize);

    /** 本商家订单详情 */
    OrderDetailVO detail(Long id);

    /** 发货；返回物流信息 */
    ShipmentVO ship(Long id, ShipmentDTO dto);

    /** 查询本商家订单的物流信息 */
    ShipmentVO shipment(Long id);
}
