package com.pandago.common.enums;

/** 订单状态，与 AGENTS 6.6 / orders.status 一致。 */
public enum OrderStatus {
    PENDING_PAYMENT("PENDING_PAYMENT", "待支付"),
    PENDING_SHIPMENT("PENDING_SHIPMENT", "待发货"),
    PENDING_RECEIPT("PENDING_RECEIPT", "待收货"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    private final String value;
    private final String description;

    OrderStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    /** 描述文本，非法值返回原值，便于排查数据异常 */
    public static String describe(String value) {
        for (OrderStatus status : values()) {
            if (status.value.equals(value)) {
                return status.description;
            }
        }
        return value == null ? "" : value;
    }
}
