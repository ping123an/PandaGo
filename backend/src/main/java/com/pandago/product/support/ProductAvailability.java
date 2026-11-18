package com.pandago.product.support;

import com.pandago.product.entity.Product;

/**
 * 商品可售性判定结果（阶段 5 购物车/收藏共用）。
 *
 * <p>购物车条目本身不保存商品状态；每次查询都重新读取商品并给出
 * "是否可结算 + 失效原因"，从而在下架、审核驳回、库存变化时提示用户重新确认。</p>
 */
public class ProductAvailability {

    private final Product product;
    private final boolean available;
    private final String reason;

    private ProductAvailability(Product product, boolean available, String reason) {
        this.product = product;
        this.available = available;
        this.reason = reason;
    }

    /** 商品存在且已上架、审核通过、有库存 */
    public static ProductAvailability available(Product product) {
        return new ProductAvailability(product, true, "");
    }

    /** 商品不存在或状态不满足，附带给用户的可读原因 */
    public static ProductAvailability unavailable(Product product, String reason) {
        return new ProductAvailability(product, false, reason);
    }

    public Product getProduct() {
        return product;
    }

    public boolean isAvailable() {
        return available;
    }

    public String getReason() {
        return reason;
    }
}
