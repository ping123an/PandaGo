package com.pandago.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 创建成功返回：前端据此跳转"下单成功"页。
 *
 * <p>兼容单订单：orderNo / totalAmount 指向批次中的第一张订单；
 * 拆单场景通过 orders 列表展示全部子订单，orderCount 为拆出的订单数量，
 * grandTotal 为本次结算所有订单的合计金额。</p>
 */
@Data
public class OrderCreateVO {
    private Long id;
    private String orderNo;
    private BigDecimal totalAmount;
    private String status;
    private String statusDescription;

    /** 结算批次号：同一次提交拆出的订单共享 */
    private String checkoutGroupId;
    /** 本次提交生成的订单数量（多商家拆单时 > 1） */
    private Integer orderCount;
    /** 本次提交所有订单的金额合计 */
    private BigDecimal grandTotal;
    /** 各子订单明细 */
    private List<OrderSummary> orders = new ArrayList<>();

    @Data
    public static class OrderSummary {
        private Long id;
        private String orderNo;
        private Long merchantId;
        private BigDecimal totalAmount;
        private String status;
        private String statusDescription;
    }
}
