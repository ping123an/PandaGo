package com.pandago.common.enums;

/**
 * 商品图片用途。
 *
 * <p>technique design 中 product_image 表没有独立的 type 列，因此本阶段用 sort 表达用途：
 * sort=1 表示主图（同时冗余在 product.main_image），sort>=2 表示详情图。
 * 请求 DTO 显式携带 type，便于校验和后续扩展，不直接信任客户端指定数据库列。</p>
 */
public enum ProductImageType {
    MAIN,
    DETAIL
}
