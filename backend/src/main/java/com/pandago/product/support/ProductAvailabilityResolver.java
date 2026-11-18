package com.pandago.product.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.ProductStatus;
import com.pandago.product.entity.Product;
import com.pandago.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 商品可售性统一判定（阶段 5）。
 *
 * <p>购物车添加、收藏添加、购物车展示都调用这里，确保"什么商品算可购买"只有一处定义。
 * 查询走 MyBatis-Plus，逻辑删除（deleted=1）由其自动追加条件，无需在 SQL 中重复写。</p>
 */
@Component
@RequiredArgsConstructor
public class ProductAvailabilityResolver {

    private final ProductMapper productMapper;

    /** 判定商品是否可购买：存在、未删除、已上架、审核通过、有库存 */
    public ProductAvailability describe(Long productId, int requiredQuantity) {
        Product product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getId, productId));
        return describe(product, requiredQuantity);
    }

    /**
     * 对已查出的商品判定可售性。购物车列表批量取出商品后逐条调用，
     * 避免每条购物车记录都查一次商品表（N+1 查询）。
     */
    public ProductAvailability describe(Product product, int requiredQuantity) {
        if (product == null) {
            return ProductAvailability.unavailable(null, "商品已删除或不存在");
        }
        if (!ProductStatus.ON_SALE.getValue().equals(product.getStatus())) {
            return ProductAvailability.unavailable(product, "商品已下架，请移除后重新选择");
        }
        if (!AuditStatus.APPROVED.getValue().equals(product.getAuditStatus())) {
            return ProductAvailability.unavailable(product, "商品未审核通过，暂不可购买");
        }
        int stock = product.getStock() == null ? 0 : product.getStock();
        if (stock <= 0) {
            return ProductAvailability.unavailable(product, "商品已售罄");
        }
        if (requiredQuantity > stock) {
            return ProductAvailability.unavailable(product,
                    "库存不足，当前仅剩 " + stock + " 件，请重新确认数量");
        }
        return ProductAvailability.available(product);
    }

    /** 只判断可见性（存在、上架、审核通过），用于收藏等不需要库存的场景 */
    public ProductAvailability describeVisible(Long productId) {
        return describeVisible(productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getId, productId)));
    }

    /** 对已查出的商品判断可见性，避免收藏列表逐条回查商品表 */
    public ProductAvailability describeVisible(Product product) {
        if (product == null) {
            return ProductAvailability.unavailable(null, "商品已删除或不存在");
        }
        if (!ProductStatus.ON_SALE.getValue().equals(product.getStatus())) {
            return ProductAvailability.unavailable(product, "商品已下架");
        }
        if (!AuditStatus.APPROVED.getValue().equals(product.getAuditStatus())) {
            return ProductAvailability.unavailable(product, "商品未审核通过");
        }
        return ProductAvailability.available(product);
    }
}
