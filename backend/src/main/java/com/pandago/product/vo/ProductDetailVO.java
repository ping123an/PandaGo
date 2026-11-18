package com.pandago.product.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 商品详情展示对象：包含图片列表，不暴露 merchantId 等内部字段。 */
@Data
public class ProductDetailVO {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String name;

    private String subtitle;

    private String mainImage;

    private BigDecimal price;

    /** 具体库存数量：仅商家端/管理端可见；用户端接口会将其置空。 */
    private Integer stock;

    /** 库存状态：IN_STOCK / OUT_OF_STOCK。用户端据此判断可否购买。 */
    private String stockStatus;

    private Integer sales;

    private String status;

    private String auditStatus;

    private String auditReason;

    private String description;

    private List<String> images = new ArrayList<>();

    private LocalDateTime createdAt;
}
