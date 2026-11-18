package com.pandago.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 商品实体，字段与 product 表一一对应。 */
@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long categoryId;

    private Long merchantId;

    private String name;

    private String subtitle;

    private String mainImage;

    private BigDecimal price;

    private Integer stock;

    private Integer sales;

    /** ON_SALE / OFF_SALE */
    private String status;

    /** PENDING / APPROVED / REJECTED */
    private String auditStatus;

    /** 审核驳回原因，审核通过时应清空 */
    private String auditReason;

    /** 审核人用户ID（阶段8） */
    private Long auditBy;

    /** 审核时间（阶段8） */
    private LocalDateTime auditTime;

    private String description;

    private Integer sort;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;
}
