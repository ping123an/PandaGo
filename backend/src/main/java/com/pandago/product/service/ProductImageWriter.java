package com.pandago.product.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.ProductImageType;
import com.pandago.common.exception.BusinessException;
import com.pandago.common.util.ImageUrlValidator;
import com.pandago.product.dto.ProductImageInputDTO;
import com.pandago.product.entity.ProductImage;
import com.pandago.product.mapper.ProductImageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 商品图片写入器。
 *
 * <p>图片记录与商品主表由调用方 Service 的 @Transactional 包在同一个事务中：
 * 任一图片 URL 校验失败或插入失败，商品主表和已插入图片一起回滚。
 * 本阶段 product_image 表没有 deleted 列，采用「删除旧记录再插入新记录」；
 * 图片不是订单快照，不会承载交易历史。</p>
 */
@Component
@RequiredArgsConstructor
public class ProductImageWriter {

    private static final int MAIN_SORT = 1;
    private static final int MAX_DETAIL_IMAGES = 9;

    private final ProductImageMapper productImageMapper;
    private final ImageUrlValidator imageUrlValidator;

    @Transactional(rollbackFor = Exception.class)
    public void replaceImages(Long productId,
                              String mainImageUrl,
                              Long mainImageSizeBytes,
                              List<ProductImageInputDTO> detailImages) {
        String validatedMain = imageUrlValidator.validate(mainImageUrl, "商品主图", mainImageSizeBytes);
        List<ProductImageInputDTO> details = detailImages == null ? List.of() : detailImages;
        if (details.size() > MAX_DETAIL_IMAGES) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "详情图最多 9 张");
        }

        Set<String> urls = new HashSet<>();
        urls.add(validatedMain);
        java.util.List<ProductImage> rows = new java.util.ArrayList<>();
        rows.add(buildRow(productId, validatedMain, MAIN_SORT));

        int sort = MAIN_SORT + 1;
        for (ProductImageInputDTO item : details) {
            if (!ProductImageType.DETAIL.name().equalsIgnoreCase(item.getType())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "详情图 type 必须为 DETAIL");
            }
            String url = imageUrlValidator.validate(item.getUrl(), "商品详情图", item.getSizeBytes());
            if (!urls.add(url)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "商品图片地址不能重复");
            }
            rows.add(buildRow(productId, url, sort++));
        }

        productImageMapper.delete(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, productId));
        for (ProductImage row : rows) {
            productImageMapper.insert(row);
        }
    }

    private ProductImage buildRow(Long productId, String url, int sort) {
        ProductImage image = new ProductImage();
        image.setProductId(productId);
        image.setUrl(url);
        image.setSort(sort);
        return image;
    }
}
