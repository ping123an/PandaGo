package com.pandago.product.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.category.entity.Category;
import com.pandago.category.mapper.CategoryMapper;
import com.pandago.product.entity.Product;
import com.pandago.product.entity.ProductImage;
import com.pandago.product.mapper.ProductImageMapper;
import com.pandago.product.vo.ProductDetailVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Entity -> 商品详情 VO 的统一转换器。
 *
 * <p>用户端、商家端和管理员端共用同一套出参映射，避免某处漏掉字段或误暴露内部字段。</p>
 */
@Component
@RequiredArgsConstructor
public class ProductViewAssembler {

    private final CategoryMapper categoryMapper;
    private final ProductImageMapper productImageMapper;

    public ProductDetailVO toDetailVO(Product product) {
        ProductDetailVO vo = new ProductDetailVO();
        vo.setId(product.getId());
        vo.setCategoryId(product.getCategoryId());
        Category category = categoryMapper.selectById(product.getCategoryId());
        vo.setCategoryName(category == null ? "" : category.getName());
        vo.setName(product.getName());
        vo.setSubtitle(product.getSubtitle());
        vo.setMainImage(product.getMainImage());
        vo.setPrice(product.getPrice());
        vo.setStock(product.getStock());
        vo.setStockStatus(product.getStock() != null && product.getStock() > 0 ? "IN_STOCK" : "OUT_OF_STOCK");
        vo.setSales(product.getSales());
        vo.setStatus(product.getStatus());
        vo.setAuditStatus(product.getAuditStatus());
        vo.setAuditReason(product.getAuditReason());
        vo.setDescription(product.getDescription());
        vo.setCreatedAt(product.getCreatedAt());

        List<ProductImage> images = productImageMapper.selectList(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, product.getId())
                .orderByAsc(ProductImage::getSort));
        vo.setImages(images.stream().map(ProductImage::getUrl).toList());
        return vo;
    }
}
