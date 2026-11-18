package com.pandago.shipment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物流信息实体（阶段 8）。
 *
 * <p>本项目只保存物流公司与物流单号，不接入真实物流平台，也不保存轨迹数据（AGENTS 1.2）。
 * 发货成功由订单条件状态更新保证，物流记录只在获得状态变更权后写入。</p>
 */
@Data
@TableName("shipment")
public class Shipment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String orderNo;

    private Long merchantId;

    /** 物流公司 */
    private String company;

    /** 物流单号 */
    private String trackingNo;

    /** 发货时间（服务端时间） */
    private LocalDateTime shippedAt;

    private LocalDateTime createdAt;
}
