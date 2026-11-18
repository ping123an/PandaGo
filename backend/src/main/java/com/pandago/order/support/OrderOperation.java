package com.pandago.order.support;

/**
 * 订单业务操作。
 *
 * <p>状态机不只描述"从哪个状态到哪个状态"，还要描述"当前状态允许做什么"。
 * 前端按钮只是展示，真正的允许与否由后端状态机判断。</p>
 */
public enum OrderOperation {

    PAY("支付"),
    CANCEL_BY_USER("用户取消"),
    SHIP("商家发货"),
    CONFIRM_RECEIPT("确认收货"),
    TIMEOUT_CLOSE("超时关闭");

    private final String description;

    OrderOperation(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
