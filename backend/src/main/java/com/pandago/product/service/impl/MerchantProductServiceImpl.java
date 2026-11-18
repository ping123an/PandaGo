package com.pandago.product.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pandago.cache.CacheKeys;
import com.pandago.cache.CacheService;
import com.pandago.category.entity.Category;
import com.pandago.category.mapper.CategoryMapper;
import com.pandago.common.api.PageResult;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.AuditStatus;
import com.pandago.common.enums.CommonStatus;
import com.pandago.common.enums.ProductStatus;
import com.pandago.common.enums.RoleCode;
import com.pandago.common.exception.BusinessException;
import com.pandago.product.dto.ProductCreateDTO;
import com.pandago.product.dto.ProductManagementQueryDTO;
import com.pandago.product.dto.ProductUpdateDTO;
import com.pandago.product.entity.Product;
import com.pandago.product.mapper.ProductMapper;
import com.pandago.product.service.MerchantProductService;
import com.pandago.product.service.ProductImageWriter;
import com.pandago.product.support.ProductSortResolver;
import com.pandago.product.support.ProductViewAssembler;
import com.pandago.product.vo.ProductDetailVO;
import com.pandago.product.vo.ProductManagementVO;
import com.pandago.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商家商品管理业务实现。
 *
 * <p>安全要点：所有方法先调用 {@link #currentMerchantId()} 校验 MERCHANT 角色并读取
 * SecurityContext 中的用户 id；查询和写操作都把这个 id 作为强制条件。
 * 客户端传来的 merchantId / userId / role 字段一律不读取。</p>
 */
@Service
@RequiredArgsConstructor
public class MerchantProductServiceImpl implements MerchantProductService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductSortResolver sortResolver;
    private final ProductImageWriter imageWriter;
    private final ProductViewAssembler viewAssembler;
    private final CacheService cacheService;

    @Override
    public PageResult<ProductManagementVO> listMyProducts(ProductManagementQueryDTO query,
                                                           long pageNum, long pageSize) {
        Long merchantId = currentMerchantId();
        ProductManagementQueryDTO safeQuery = query == null ? new ProductManagementQueryDTO() : query;
        safeQuery.setMerchantId(merchantId);
        sortResolver.apply(safeQuery);

        IPage<ProductManagementVO> page = new Page<>(pageNum, pageSize);
        List<ProductManagementVO> records = productMapper.selectProductManagementList(safeQuery, page);
        return PageResult.of(records, page.getTotal(), pageNum, pageSize, page.getPages());
    }

    @Override
    public ProductDetailVO detail(Long id) {
        Long merchantId = currentMerchantId();
        return viewAssembler.toDetailVO(requireOwnedProduct(id, merchantId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProductCreateDTO dto) {
        Long merchantId = currentMerchantId();
        requireEnabledCategory(dto.getCategoryId());

        Product product = new Product();
        product.setMerchantId(merchantId);
        product.setCategoryId(dto.getCategoryId());
        product.setName(dto.getName().trim());
        product.setSubtitle(dto.getSubtitle());
        product.setMainImage(dto.getMainImage());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setSales(0);
        // PRD 2.2.2：新增商品默认待审核且下架
        product.setAuditStatus(AuditStatus.PENDING.getValue());
        product.setStatus(ProductStatus.OFF_SALE.getValue());
        product.setDescription(dto.getDescription());
        product.setSort(dto.getSort() == null ? 0 : dto.getSort());
        productMapper.insert(product);

        imageWriter.replaceImages(product.getId(), dto.getMainImage(),
                dto.getMainImageSizeBytes(), dto.getDetailImages());
        invalidateProduct(product.getId());
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProductUpdateDTO dto) {
        Long merchantId = currentMerchantId();
        Product product = requireOwnedProduct(id, merchantId);
        requireEnabledCategory(dto.getCategoryId());

        product.setCategoryId(dto.getCategoryId());
        product.setName(dto.getName().trim());
        product.setSubtitle(dto.getSubtitle());
        String mainImage = dto.getMainImage() == null || dto.getMainImage().isBlank()
                ? product.getMainImage() : dto.getMainImage();
        product.setMainImage(mainImage);
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setDescription(dto.getDescription());
        if (dto.getSort() != null) {
            product.setSort(dto.getSort());
        }
        productMapper.updateById(product);

        imageWriter.replaceImages(id, mainImage, dto.getMainImageSizeBytes(), dto.getDetailImages());
        invalidateProduct(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        Long merchantId = currentMerchantId();
        Product product = requireOwnedProduct(id, merchantId);
        if (!ProductStatus.ON_SALE.getValue().equals(status)
                && !ProductStatus.OFF_SALE.getValue().equals(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "状态只能为 ON_SALE 或 OFF_SALE");
        }
        if (ProductStatus.ON_SALE.getValue().equals(status)
                && !AuditStatus.APPROVED.getValue().equals(product.getAuditStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "商品未审核通过，不能上架");
        }
        product.setStatus(status);
        productMapper.updateById(product);
        invalidateProduct(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForAudit(Long id) {
        Long merchantId = currentMerchantId();
        Product product = requireOwnedProduct(id, merchantId);
        if (AuditStatus.APPROVED.getValue().equals(product.getAuditStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "商品已审核通过，无需重复提交");
        }
        product.setAuditStatus(AuditStatus.PENDING.getValue());
        product.setStatus(ProductStatus.OFF_SALE.getValue());
        product.setAuditReason(null);
        productMapper.updateById(product);
        invalidateProduct(id);
    }

    /** 商品写操作后清理用户端可能缓存的详情与热门列表。 */
    private void invalidateProduct(Long id) {
        cacheService.evict(CacheKeys.productDetail(id));
        cacheService.evictByPrefix(CacheKeys.PRODUCT_HOT_PREFIX);
    }

    /** 角色校验 + 当前用户 id 读取：必须在任何归属查询之前执行。 */
    private Long currentMerchantId() {
        SecurityUtils.requireRole(RoleCode.MERCHANT);
        return SecurityUtils.requireCurrentUser().getUserId();
    }

    /** 归属校验：找不到是 404；找到但不属于当前商家是 403，避免泄露商品是否存在。 */
    private Product requireOwnedProduct(Long id, Long merchantId) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        if (product.getMerchantId() == null || !merchantId.equals(product.getMerchantId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己创建的商品");
        }
        return product;
    }

    private void requireEnabledCategory(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || category.getStatus() == null
                || category.getStatus() != CommonStatus.ENABLED.getValue()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分类不存在或已禁用");
        }
    }
}
