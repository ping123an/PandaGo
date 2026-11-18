package com.pandago.order.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 订单明细 VO：全部来自下单快照。 */
@Data
public class OrderItemVO {
    private Long id;
    private Long productId;
    private String productName;
    private String productImage;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal subtotal;
}
