package com.pandago.shipment.service;

import com.pandago.shipment.vo.ShipmentVO;

/** 用户端物流查询（阶段 8）。 */
public interface ShipmentQueryService {

    /** 按订单 ID 查询当前登录用户自己订单的物流；未发货返回明确的业务结果 */
    ShipmentVO mineByOrderId(Long orderId);
}
