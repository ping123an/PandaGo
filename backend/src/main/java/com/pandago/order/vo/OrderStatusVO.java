package com.pandago.order.vo;

import lombok.Data;

import java.util.List;

/** 轻量订单状态查询 VO：供前端在列表/详情中决定展示哪些操作按钮。 */
@Data
public class OrderStatusVO {

    private Long id;
    private String orderNo;
    private String status;
    private String statusDescription;

    /** 后端状态机计算出的可执行操作，如 PAY / CANCEL_BY_USER */
    private List<String> allowedOperations;

    /** 便捷布尔字段，避免前端重复解析操作列表 */
    private boolean canPay;
    private boolean canCancel;
    private boolean canShip;
    private boolean canConfirm;
    private boolean canReview;
}
