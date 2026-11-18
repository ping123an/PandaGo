package com.pandago.payment.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付结果与支付状态查询返回对象。
 *
 * <p>未支付时 paymentNo / paidAt 为 null，paid=false，payAmount 显示订单应付金额；
 * 已支付时 payAmount 为实际支付金额（同一来源：orders.total_amount）。</p>
 */
@Data
public class PaymentVO {

    private Long id;
    private String paymentNo;
    private Long orderId;
    private String orderNo;
    private BigDecimal payAmount;
    private String paymentMethod;
    private String status;
    private LocalDateTime paidAt;
    private boolean paid;

    /** 当前订单状态，便于前端支付成功后直接更新按钮与标签 */
    private String orderStatus;
    private String orderStatusDescription;

    /** 订单创建时间（快照），支付页展示用 */
    private LocalDateTime createdAt;
}
