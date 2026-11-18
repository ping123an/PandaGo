package com.pandago.shipment.service;

import com.pandago.order.entity.Orders;
import com.pandago.shipment.dto.ShipmentDTO;
import com.pandago.shipment.vo.ShipmentVO;

/** 商家发货事务入口（阶段 8）：条件状态更新 + 物流记录同事务。 */
public interface ShipmentTransactionService {

    /** 在事务内完成发货：先取得状态变更权，再写物流记录 */
    ShipmentVO ship(Orders submittedOrder, Long merchantId, ShipmentDTO dto);
}
