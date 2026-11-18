package com.pandago.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.pandago.admin.service.OperationLogService;
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
import com.pandago.product.dto.ProductAuditDTO;
import com.pandago.product.dto.ProductCreateDTO;
import com.pandago.product.dto.ProductManagementQueryDTO;
import com.pandago.product.dto.ProductQueryDTO;
import com.pandago.product.dto.ProductUpdateDTO;
import com.pandago.product.entity.Product;
import com.pandago.product.mapper.ProductMapper;
import com.pandago.product.service.ProductImageWriter;
import com.pandago.product.service.ProductService;
import com.pandago.product.support.ProductSortResolver;
import com.pandago.product.support.ProductViewAssembler;
import com.pandago.product.vo.ProductDetailVO;
import com.pandago.product.vo.ProductListVO;
import com.pandago.product.vo.ProductManagementVO;
import com.pandago.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 用户端浏览 + 管理端审核/基础维护。
 *
 * <p>关键约束（PRD 2.1.5 / 2.3.4 / AGENTS 6.2）：
 * 1. 用户端只展示审核通过、上架、未删除的商品；
 * 2. 排序字段必须通过服务端白名单，未知值返回 400；
 * 3. 审核通过后商家仍需主动上架，驳回商品强制下架。</p>
 */
@Service
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final CategoryMapper categoryMapper;
    private final ProductSortResolver sortResolver;
    private final ProductImageWriter imageWriter;
    private final ProductViewAssembler viewAssembler;
    private final OperationLogService operationLogService;
    private final CacheService cacheService;

    public ProductServiceImpl(ProductMapper productMapper,
                              CategoryMapper categoryMapper,
                              ProductSortResolver sortResolver,
                              ProductImageWriter imageWriter,
                              ProductViewAssembler viewAssembler,
                              OperationLogService operationLogService,
                              CacheService cacheService) {
        this.productMapper = productMapper;
        this.categoryMapper = categoryMapper;
        this.sortResolver = sortResolver;
        this.imageWriter = imageWriter;
        this.viewAssembler = viewAssembler;
        this.operationLogService = operationLogService;
        this.cacheService = cacheService;
    }

    @Override
    public PageResult<ProductListVO> page(ProductQueryDTO query, long pageNum, long pageSize) {
        ProductQueryDTO safeQuery = query == null ? new ProductQueryDTO() : query;
        validatePriceRange(safeQuery.getMinPrice(), safeQuery.getMaxPrice());
        sortResolver.apply(safeQuery);

        IPage<ProductListVO> page = new Page<>(pageNum, pageSize);
        List<ProductListVO> records = productMapper.selectProductList(safeQuery, page);
        return PageResult.of(records, page.getTotal(), pageNum, pageSize, page.getPages());
    }

    @Override
    public List<ProductListVO> hot(int limit) {
        // Cache Aside：先读缓存，未命中回源 MySQL 并回填；Redis 异常时 get 返回 empty 自动降级。
        String key = CacheKeys.productHot(limit);
        Optional<List<ProductListVO>> cached = cacheService.get(key, new TypeReference<List<ProductListVO>>() {});
        if (cached.isPresent()) {
            return cached.get();
        }
        List<ProductListVO> products = productMapper.selectHotProducts(limit);
        cacheService.set(key, products, CacheKeys.HOT_TTL);
        return products;
    }

    @Override
    public ProductDetailVO detail(Long id) {
        // Cache Aside：命中直接返回；未命中查库回填。空值缓存 30s 防穿透。
        // 缓存中保留完整 VO（含具体库存）供管理端/内部使用；用户端返回前脱敏，
        // 不向普通购物用户暴露具体库存数量，只提供 stockStatus。
        String key = CacheKeys.productDetail(id);
        Optional<ProductDetailVO> cached = cacheService.get(key, ProductDetailVO.class);
        if (cached.isPresent()) {
            return toPublicDetail(cached.get());
        }
        if (cacheService.isNull(key)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已下架");
        }
        Product product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getId, id)
                .eq(Product::getStatus, ProductStatus.ON_SALE.getValue())
                .eq(Product::getAuditStatus, AuditStatus.APPROVED.getValue()));
        if (product == null) {
            cacheService.setNull(key, CacheKeys.NULL_TTL);
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已下架");
        }
        ProductDetailVO vo = viewAssembler.toDetailVO(product);
        cacheService.set(key, vo, CacheKeys.DETAIL_TTL);
        return toPublicDetail(vo);
    }

    /** 用户端详情脱敏：拷贝一份并清空具体库存，仅保留库存状态，避免污染缓存对象。 */
    private ProductDetailVO toPublicDetail(ProductDetailVO source) {
        ProductDetailVO vo = new ProductDetailVO();
        vo.setId(source.getId());
        vo.setCategoryId(source.getCategoryId());
        vo.setCategoryName(source.getCategoryName());
        vo.setName(source.getName());
        vo.setSubtitle(source.getSubtitle());
        vo.setMainImage(source.getMainImage());
        vo.setPrice(source.getPrice());
        vo.setStock(null);
        // 兼容改造前已缓存、尚无 stockStatus 的旧数据：按库存兜底推导
        String status = source.getStockStatus() != null
                ? source.getStockStatus()
                : (source.getStock() != null && source.getStock() > 0 ? "IN_STOCK" : "OUT_OF_STOCK");
        vo.setStockStatus(status);
        vo.setSales(source.getSales());
        vo.setStatus(source.getStatus());
        vo.setAuditStatus(source.getAuditStatus());
        vo.setAuditReason(source.getAuditReason());
        vo.setDescription(source.getDescription());
        vo.setImages(source.getImages());
        vo.setCreatedAt(source.getCreatedAt());
        return vo;
    }

    @Override
    public ProductDetailVO adminDetail(Long id) {
        return viewAssembler.toDetailVO(requireExistingProduct(id));
    }

    @Override
    public PageResult<ProductManagementVO> adminPage(ProductManagementQueryDTO query,
                                                      long pageNum, long pageSize) {
        ProductManagementQueryDTO safeQuery = query == null ? new ProductManagementQueryDTO() : query;
        sortResolver.apply(safeQuery);
        IPage<ProductManagementVO> page = new Page<>(pageNum, pageSize);
        List<ProductManagementVO> records = productMapper.selectProductManagementList(safeQuery, page);
        // 平台演示商品 merchant_id=0 没有对应用户，兜底文案放在 Java 层，
        // 避免在 SQL 中写中文字面量引发不同环境字符集差异。
        for (ProductManagementVO vo : records) {
            if (vo.getMerchantName() == null || vo.getMerchantName().isBlank()) {
                vo.setMerchantName("平台自营");
            }
        }
        return PageResult.of(records, page.getTotal(), pageNum, pageSize, page.getPages());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, ProductAuditDTO dto) {
        // 业务层第二道防线：即使 URL/@PreAuthorize 配置遗漏，商家/普通用户也不能审核
        SecurityUtils.requireRole(RoleCode.ADMIN);
        Long adminId = SecurityUtils.requireCurrentUser().getUserId();
        Product product = requireExistingProduct(id);
        String target = dto.getAuditStatus();
        String current = product.getAuditStatus();
        // 防止重复审核：目标状态与当前审核状态一致时直接拒绝，避免重复留痕/覆盖审核人
        if (target.equals(current)) {
            String label = AuditStatus.PENDING.getValue().equals(current) ? "待审核"
                    : AuditStatus.APPROVED.getValue().equals(current) ? "已通过" : "已驳回";
            throw new BusinessException(ResultCode.CONFLICT, "商品当前已是" + label + "状态，请勿重复审核");
        }
        // 已驳回商品必须先由商家修改并重新提交审核，管理员不能直接放行
        if (AuditStatus.REJECTED.getValue().equals(current)
                && AuditStatus.APPROVED.getValue().equals(target)) {
            throw new BusinessException(ResultCode.CONFLICT, "商品已被驳回，需商家重新提交审核后才能通过");
        }
        String reason = null;
        if (AuditStatus.APPROVED.getValue().equals(target)) {
            product.setAuditStatus(AuditStatus.APPROVED.getValue());
            product.setAuditReason(null);
        } else if (AuditStatus.REJECTED.getValue().equals(target)) {
            reason = dto.getReason() == null ? null : dto.getReason().trim();
            // 驳回必须填写原因：前后端双重校验，后端为最终防线
            if (reason == null || reason.isEmpty()) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "驳回必须填写原因");
            }
            product.setAuditStatus(AuditStatus.REJECTED.getValue());
            product.setStatus(ProductStatus.OFF_SALE.getValue());   // 驳回强制下架
            product.setAuditReason(reason);
        } else {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的审核结果");
        }
        // 审核留痕：审核人、审核时间、审核意见（意见存 audit_reason 并写入操作日志）
        product.setAuditBy(adminId);
        product.setAuditTime(LocalDateTime.now());
        productMapper.updateById(product);
        // 审核改变可见性/状态：清详情与热门缓存，避免缓存中仍是旧审核状态
        invalidateProduct(product.getId());
        operationLogService.record("PRODUCT", "AUDIT", "PRODUCT", product.getId(),
                "审核结果=" + target + (reason == null ? "" : "，意见=" + reason));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ProductCreateDTO dto) {
        requireEnabledCategory(dto.getCategoryId());

        Product product = new Product();
        product.setCategoryId(dto.getCategoryId());
        // 管理端维护的是平台演示商品；商家商品只能通过 /merchant/products 创建。
        product.setMerchantId(0L);
        product.setName(dto.getName().trim());
        product.setSubtitle(dto.getSubtitle());
        product.setMainImage(dto.getMainImage());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
        product.setSales(0);
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
        Product product = requireExistingProduct(id);
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
        Product product = requireExistingProduct(id);
        if (!ProductStatus.ON_SALE.getValue().equals(status)
                && !ProductStatus.OFF_SALE.getValue().equals(status)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "状态只能为 ON_SALE 或 OFF_SALE");
        }
        if (ProductStatus.ON_SALE.getValue().equals(status)
                && !AuditStatus.APPROVED.getValue().equals(product.getAuditStatus())) {
            String label = AuditStatus.PENDING.getValue().equals(product.getAuditStatus())
                    ? "商品待审核，不能上架" : "商品审核已驳回，不能上架";
            throw new BusinessException(ResultCode.CONFLICT, label);
        }
        product.setStatus(status);
        productMapper.updateById(product);
        invalidateProduct(id);
    }

    /**
     * 商品写操作后的缓存失效：删该商品详情 + 清所有热门分页缓存。
     * 注意不主动回写缓存（Cache Aside 的"删"而非"更新"），避免并发下写回旧值。
     */
    private void invalidateProduct(Long id) {
        cacheService.evict(CacheKeys.productDetail(id));
        cacheService.evictByPrefix(CacheKeys.PRODUCT_HOT_PREFIX);
    }

    /** 用户端可见：已上架 + 审核通过 + 未删除（逻辑删除由 MyBatis-Plus 自动追加） */
    private Product requireVisibleProduct(Long id) {
        Product product = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getId, id)
                .eq(Product::getStatus, ProductStatus.ON_SALE.getValue())
                .eq(Product::getAuditStatus, AuditStatus.APPROVED.getValue()));
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已下架");
        }
        return product;
    }

    private Product requireExistingProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
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

    private void validatePriceRange(BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "最低价格不能大于最高价格");
        }
    }

}
