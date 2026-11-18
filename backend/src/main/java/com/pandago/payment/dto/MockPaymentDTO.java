package com.pandago.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 模拟支付请求。
 *
 * <p>只接收订单号，不声明 amount / payAmount / totalAmount 等金额字段。
 * 即使客户端额外夹带这些字段，Jackson 也会忽略；实际支付金额永远读取数据库订单快照。</p>
 */
@Data
public class MockPaymentDTO {

    @NotBlank(message = "订单号不能为空")
    @Size(max = 32, message = "订单号长度不能超过 32")
    private String orderNo;

    /**
     * 支付方式（模拟）：ALIPAY / WECHAT / MOCK，缺省 MOCK。
     * 只是展示与记录用的标记，不影响金额与状态流转；仍不接真实第三方接口。
     */
    @Size(max = 16, message = "支付方式过长")
    private String paymentMethod;
}
