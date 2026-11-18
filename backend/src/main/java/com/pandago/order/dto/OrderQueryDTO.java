package com.pandago.order.dto;

import lombok.Data;

/** 订单分页/状态筛选查询参数。 */
@Data
public class OrderQueryDTO {

    /** 可选状态筛选：PENDING_PAYMENT / PENDING_SHIPMENT / ...；留空表示全部 */
    private String status;
}
