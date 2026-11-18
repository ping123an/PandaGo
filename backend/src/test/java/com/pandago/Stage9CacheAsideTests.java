package com.pandago;

import com.pandago.cache.CacheKeys;
import com.pandago.cache.CacheService;
import com.pandago.category.entity.Category;
import com.pandago.category.mapper.CategoryMapper;
import com.pandago.category.service.CategoryService;
import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.CommonStatus;
import com.pandago.common.enums.ProductStatus;
import com.pandago.product.dto.ProductStatusDTO;
import com.pandago.product.dto.ProductUpdateDTO;
import com.pandago.product.entity.Product;
import com.pandago.product.mapper.ProductMapper;
import com.pandago.product.service.ProductService;
import com.pandago.product.vo.ProductDetailVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cache Aside 业务链路测试（阶段 9）。
 *
 * <p>用 {@code @MockBean CacheService} 替换真实缓存，验证 Service 层是否按 Cache Aside
 * 正确读缓存、未命中回源、写后失效。类级 {@code @Transactional} 让商品/分类数据改动
 * 在用例结束后回滚，不污染初始化数据。</p>
 *
 * <p>注意：这里验证的是"业务层是否正确调用缓存"，不验证 Redis 协议细节；
 * Redis 命中/未命中/降级由 {@link Stage9CacheFallbackTests} 覆盖。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Stage9CacheAsideTests {

    @Autowired private ProductService productService;
    @Autowired private CategoryService categoryService;
    @Autowired private ProductMapper productMapper;
    @Autowired private CategoryMapper categoryMapper;

    @MockBean private CacheService cacheService;

    private Product insertVisibleProduct(String name) {
        Product p = new Product();
        p.setCategoryId(firstCategoryId());
        p.setMerchantId(0L);
        p.setName(name);
        p.setMainImage("/images/panda-product.svg");
        p.setPrice(new BigDecimal("88.00"));
        p.setStock(30);
        p.setSales(0);
        p.setStatus(ProductStatus.ON_SALE.getValue());
        p.setAuditStatus(AuditStatus.APPROVED.getValue());
        p.setSort(0);
        productMapper.insert(p);
        return p;
    }

    private Long firstCategoryId() {
        return categoryMapper.selectList(null).get(0).getId();
    }

    @Test
    void cacheHitReturnsCachedValueWithoutDatabase() {
        // 用一个根本不存在的 id，若业务真的命中缓存直接返回，就不会因查不到而抛异常
        long fakeId = 987654321L;
        ProductDetailVO cached = new ProductDetailVO();
        cached.setId(fakeId);
        cached.setName("来自缓存的商品");
        when(cacheService.get(CacheKeys.productDetail(fakeId), ProductDetailVO.class))
                .thenReturn(Optional.of(cached));

        ProductDetailVO got = productService.detail(fakeId);

        assertNotNull(got);
        assertEquals("来自缓存的商品", got.getName(), "命中缓存应直接返回缓存对象，不回源数据库");
        verify(cacheService).get(CacheKeys.productDetail(fakeId), ProductDetailVO.class);
    }

    @Test
    void cacheMissReadsDatabaseAndWritesBack() {
        Product product = insertVisibleProduct("S9-回源商品");
        when(cacheService.get(eq(CacheKeys.productDetail(product.getId())), eq(ProductDetailVO.class)))
                .thenReturn(Optional.empty());
        when(cacheService.isNull(CacheKeys.productDetail(product.getId()))).thenReturn(false);

        ProductDetailVO got = productService.detail(product.getId());

        assertEquals("S9-回源商品", got.getName());
        verify(cacheService).set(eq(CacheKeys.productDetail(product.getId())), any(ProductDetailVO.class), eq(CacheKeys.DETAIL_TTL));
    }

    @Test
    void productUpdateEvictsDetailAndHotCache() {
        Product product = insertVisibleProduct("S9-更新前");
        ProductUpdateDTO dto = new ProductUpdateDTO();
        dto.setCategoryId(product.getCategoryId());
        dto.setName("S9-更新后");
        dto.setPrice(new BigDecimal("99.00"));
        dto.setStock(20);

        productService.update(product.getId(), dto);

        verify(cacheService).evict(CacheKeys.productDetail(product.getId()));
        verify(cacheService).evictByPrefix(CacheKeys.PRODUCT_HOT_PREFIX);
    }

    @Test
    void productOffShelfEvictsDetailAndHotCache() {
        Product product = insertVisibleProduct("S9-下架前");
        ProductStatusDTO dto = new ProductStatusDTO();
        dto.setStatus(ProductStatus.OFF_SALE.getValue());

        productService.updateStatus(product.getId(), dto.getStatus());

        verify(cacheService).evict(CacheKeys.productDetail(product.getId()));
        verify(cacheService).evictByPrefix(CacheKeys.PRODUCT_HOT_PREFIX);
    }

    @Test
    void categoryChangeEvictsCategoryListCache() {
        Category category = categoryMapper.selectList(null).get(0);

        categoryService.updateStatus(category.getId(), CommonStatus.DISABLED.getValue());

        verify(cacheService).evict(CacheKeys.CATEGORY_LIST);
    }
}
