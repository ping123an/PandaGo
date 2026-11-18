package com.pandago.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表。
 *
 * <p>收货人信息与金额均为下单时刻的快照，商品后续改名/改价、地址后续修改或删除，
 * 都不会影响历史订单（PRD 2.1.10、AGENTS 五）。订单不设 deleted 字段：
 * 交易历史通过 status 流转，不因普通用户操作物理删除。</p>
 */
@Data
@TableName("orders")
public class Orders {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

    private Long merchantId;

    /** 结算批次号：同一次购物车提交拆出的多张订单共享，配合 merchant_id 做幂等（V7） */
    private String checkoutGroupId;

    /** 订单来源：CART-购物车结算，DIRECT-立即购买（V8）；历史订单默认 CART */
    private String source;

    private BigDecimal totalAmount;

    /** PENDING_PAYMENT / PENDING_SHIPMENT / PENDING_RECEIPT / COMPLETED / CANCELLED */
    private String status;

    private String remark;

    private String receiverName;

    private String receiverPhone;

    private String receiverProvince;

    private String receiverCity;

    private String receiverDistrict;

    private String receiverDetailAddress;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 确认收货时间，PENDING_RECEIPT -> COMPLETED 时写入 */
    private LocalDateTime receivedAt;

    /** 关联明细，非 orders 表字段，仅用于详情装配 */
    @TableField(exist = false)
    private java.util.List<OrderItem> items;
}
