package com.pandago.order.vo;

import lombok.Data;

/** 用户取消订单的结果。库存是否回补由数据库最终结果为准，此 VO 只反馈状态流转。 */
@Data
public class OrderCancelVO {

    private Long id;
    private String orderNo;
    private String status;
    private String statusDescription;
}
