package com.pandago.cart.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车条目展示对象。
 *
 * <p>price / subtotal 来自当前 product 表，不是购物车快照；
 * available=false 时 invalidReason 告诉用户为什么需要重新确认。</p>
 */
@Data
public class CartItemVO {

    private Long id;

    private Long productId;

    private String productName;

    private String mainImage;

    /** 商家 ID，来自 product.merchant_id，供前端按商家分组（不在请求中信任） */
    private Long merchantId;

    /** 商家展示名称，商家已删除时回退为「商家已注销」 */
    private String merchantName;

    /** 当前数据库价格；商品已删除时为 null */
    private BigDecimal price;

    private Integer quantity;

    private Integer stock;

    private Boolean selected;

    /** 是否可以进入结算：上架 + 审核通过 + 未删除 + 库存充足 */
    private Boolean available;

    /** 不可结算原因；available=true 时为空字符串 */
    private String invalidReason;

    /** 预估小计 = 当前价格 × 数量；不可购买商品为 0 */
    private BigDecimal subtotal;
}
