package com.pandago.favorite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.common.api.ResultCode;
import com.pandago.common.exception.BusinessException;
import com.pandago.favorite.dto.FavoriteAddDTO;
import com.pandago.favorite.entity.Favorite;
import com.pandago.favorite.mapper.FavoriteMapper;
import com.pandago.favorite.service.FavoriteService;
import com.pandago.favorite.vo.FavoriteVO;
import com.pandago.product.entity.Product;
import com.pandago.product.mapper.ProductMapper;
import com.pandago.product.support.ProductAvailability;
import com.pandago.product.support.ProductAvailabilityResolver;
import com.pandago.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收藏业务实现（阶段 5）。
 *
 * <p>唯一性由 favorite 表的 uk_favorite_user_product(user_id, product_id) 保证；
 * Service 捕获唯一键冲突转成 409，而不是先查再插的非原子写法。
 * 他人收藏无法被取消：删除条件永远带上当前用户 id，返回 404。</p>
 */
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final ProductMapper productMapper;
    private final ProductAvailabilityResolver availabilityResolver;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(FavoriteAddDTO dto) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();

        // 收藏时也要求商品当前可售，避免收藏无意义的下架/未审核商品
        ProductAvailability availability = availabilityResolver.describeVisible(dto.getProductId());
        if (!availability.isAvailable()) {
            throw new BusinessException(ResultCode.CONFLICT, availability.getReason());
        }

        Favorite favorite = new Favorite();
        favorite.setUserId(userId);
        favorite.setProductId(dto.getProductId());
        try {
            favoriteMapper.insert(favorite);
            return favorite.getId();
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ResultCode.CONFLICT, "该商品已在收藏列表中");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeByProduct(Long productId) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        int rows = favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
        if (rows == 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "收藏记录不存在");
        }
    }

    @Override
    public List<FavoriteVO> listMyFavorites() {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        List<Favorite> favorites = favoriteMapper.selectList(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .orderByDesc(Favorite::getCreatedAt));
        if (favorites.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = favorites.stream().map(Favorite::getProductId).toList();
        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : productMapper.selectBatchIds(productIds)) {
            productMap.put(product.getId(), product);
        }

        List<FavoriteVO> result = new ArrayList<>(favorites.size());
        for (Favorite favorite : favorites) {
            Product product = productMap.get(favorite.getProductId());
            FavoriteVO vo = new FavoriteVO();
            vo.setId(favorite.getId());
            vo.setProductId(favorite.getProductId());
            vo.setCreatedAt(favorite.getCreatedAt());
            if (product == null) {
                vo.setProductName("商品已删除");
                vo.setMainImage("");
                vo.setAvailable(false);
                vo.setReason("商品已删除或不存在");
            } else {
                vo.setProductName(product.getName());
                vo.setMainImage(product.getMainImage());
                vo.setPrice(product.getPrice());
                vo.setStatus(product.getStatus());
                vo.setAuditStatus(product.getAuditStatus());
                ProductAvailability availability = availabilityResolver.describeVisible(product);
                vo.setAvailable(availability.isAvailable());
                vo.setReason(availability.getReason());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public boolean isFavorited(Long productId) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        Long count = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getProductId, productId));
        return count != null && count > 0;
    }
}
