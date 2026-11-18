package com.pandago.shipment.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 物流信息 VO（阶段 8）。
 *
 * <p>刻意不提供任何"物流轨迹"字段：本项目没有真实轨迹数据，
 * 界面只展示物流公司、单号与发货时间，不伪造节点（AGENTS 1.2 / PRD 三）。</p>
 */
@Data
public class ShipmentVO {

    private Long id;
    private Long orderId;
    private String orderNo;
    private String company;
    private String trackingNo;
    private LocalDateTime shippedAt;
}
