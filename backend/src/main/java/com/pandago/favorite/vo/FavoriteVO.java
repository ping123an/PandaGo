package com.pandago.favorite.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 收藏列表展示对象。
 *
 * <p>商品后续下架或删除时收藏关系仍保留，通过 available / reason 合理展示状态，
 * 不直接把失效商品从列表中悄悄抹掉（PRD 2.1.9）。</p>
 */
@Data
public class FavoriteVO {

    private Long id;

    private Long productId;

    private String productName;

    private String mainImage;

    /** 商品表当前价格；商品已删除时为 null */
    private BigDecimal price;

    private String status;

    private String auditStatus;

    private Boolean available;

    private String reason;

    private LocalDateTime createdAt;
}
