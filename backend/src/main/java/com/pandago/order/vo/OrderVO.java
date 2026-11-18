package com.pandago.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 订单列表项 VO：来自订单主表快照，不依赖商品当前数据。 */
@Data
public class OrderVO {
    private Long id;
    private String orderNo;
    private BigDecimal totalAmount;
    private String status;
    /** 状态中文描述，如"待支付"，由后端枚举映射，前端无需维护映射表 */
    private String statusDescription;
    private String remark;
    private LocalDateTime createdAt;
    /** 列表页展示用：首件商品快照名称与图片 */
    private String firstProductName;
    private String firstProductImage;
    private Integer itemCount;
    /** 列表内联展示的明细（详情接口返回完整地址快照） */
    private List<OrderItemVO> items;
}
