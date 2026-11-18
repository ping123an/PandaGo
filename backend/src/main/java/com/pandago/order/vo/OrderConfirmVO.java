package com.pandago.order.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 确认收货结果 VO（阶段 8）。 */
@Data
public class OrderConfirmVO {
    private Long id;
    private String orderNo;
    private String status;
    private String statusDescription;
    /** 服务端记录的确认收货时间 */
    private LocalDateTime receivedAt;
}
