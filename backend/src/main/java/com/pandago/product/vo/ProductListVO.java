package com.pandago.product.vo;

import lombok.Data;

import java.math.BigDecimal;

/** 商品列表卡片展示对象。 */
@Data
public class ProductListVO {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String name;

    private String subtitle;

    private String mainImage;

    private BigDecimal price;

    /** 库存状态：IN_STOCK / OUT_OF_STOCK。用户端不暴露具体库存数量。 */
    private String stockStatus;

    private Integer sales;

    private String status;
}
