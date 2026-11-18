package com.pandago.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟支付记录。
 *
 * <p>第一版只记录 SUCCESS，不接真实支付平台。payment_no 唯一索引是幂等性的数据库兜底；
 * order_id 也设唯一索引，保证同一订单最多只有一条成功支付记录。</p>
 */
@Data
@TableName("payment_record")
public class PaymentRecord {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String METHOD_MOCK = "MOCK";
    /** 模拟支付宝 / 微信支付：仅作支付方式标记，不接真实第三方接口 */
    public static final String METHOD_ALIPAY = "ALIPAY";
    public static final String METHOD_WECHAT = "WECHAT";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 支付流水号，后端生成，唯一索引兜底 */
    private String paymentNo;

    private Long orderId;

    private String orderNo;

    private Long userId;

    /** 实际支付金额：只从 orders.total_amount 读取，不接受前端传值 */
    private BigDecimal payAmount;

    /** 支付方式：本项目固定 MOCK */
    private String paymentMethod;

    private String status;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;
}
