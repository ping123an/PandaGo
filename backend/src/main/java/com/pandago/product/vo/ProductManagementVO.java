package com.pandago.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商家 / 管理员商品管理列表展示对象；不暴露密码、密钥等无关字段。 */
@Data
public class ProductManagementVO {

    private Long id;

    private Long categoryId;

    private String categoryName;

    /** 商家名称（平台自营商品为「平台自营」） */
    private String merchantName;

    private String name;

    private String mainImage;

    private BigDecimal price;

    private Integer stock;

    private Integer sales;

    private String status;

    private String auditStatus;

    private String auditReason;

    /** 审核人用户 ID */
    private Long auditBy;

    /** 审核时间 */
    private LocalDateTime auditTime;

    private Integer sort;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
