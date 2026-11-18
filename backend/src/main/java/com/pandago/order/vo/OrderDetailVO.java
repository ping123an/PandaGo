package com.pandago.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 订单详情 VO：主表快照 + 地址快照 + 商品明细快照。 */
@Data
public class OrderDetailVO {
    private Long id;
    private String orderNo;
    private BigDecimal totalAmount;
    private String status;
    private String statusDescription;
    private String remark;
    private LocalDateTime createdAt;

    /** 收货地址快照 */
    private String receiverName;
    private String receiverPhone;
    private String receiverProvince;
    private String receiverCity;
    private String receiverDistrict;
    private String receiverDetailAddress;

    private List<OrderItemVO> items;
}
