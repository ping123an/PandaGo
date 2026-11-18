package com.pandago.cart.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pandago.cart.dto.CartAddDTO;
import com.pandago.cart.dto.CartUpdateDTO;
import com.pandago.cart.entity.CartItem;
import com.pandago.cart.mapper.CartItemMapper;
import com.pandago.cart.service.CartService;
import com.pandago.cart.vo.CartItemVO;
import com.pandago.cart.vo.CartVO;
import com.pandago.common.api.ResultCode;
import com.pandago.common.exception.BusinessException;
import com.pandago.product.entity.Product;
import com.pandago.product.mapper.ProductMapper;
import com.pandago.product.support.ProductAvailability;
import com.pandago.product.support.ProductAvailabilityResolver;
import com.pandago.security.SecurityUtils;
import com.pandago.user.entity.User;
import com.pandago.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 购物车业务实现（阶段 5）。
 *
 * <p>核心规则：
 * 1. userId 只从 SecurityContext 读取，杜绝修改请求参数操作他人购物车；
 * 2. 同一用户同一商品一行：唯一索引兜底 + 重复添加走原子累加；
 * 3. 添加时校验商品存在/上架/审核通过、数量为正且不超过库存；
 * 4. 展示价格实时来自 product，购物车不存价格，也不作为成交价；
 * 5. 商品状态变化时在查询结果中标记 available=false + 原因。</p>
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;
    private final ProductAvailabilityResolver availabilityResolver;
    private final UserMapper userMapper;

    @Override
    public CartVO getMyCart() {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();

        List<CartItem> items = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getCreatedAt));

        if (items.isEmpty()) {
            CartVO empty = new CartVO();
            empty.setItems(List.of());
            empty.setTotalQuantity(0);
            empty.setEstimatedTotal(BigDecimal.ZERO.setScale(2));
            empty.setInvalidCount(0);
            empty.setAllSelected(false);
            return empty;
        }

        // 批量取商品，避免 N+1；以 id 建索引供逐条关联
        List<Long> productIds = items.stream().map(CartItem::getProductId).toList();
        Map<Long, Product> productMap = new HashMap<>();
        for (Product product : productMapper.selectBatchIds(productIds)) {
            productMap.put(product.getId(), product);
        }

        // 批量取商家名称：商家 ID 来自商品，不信任前端；一次查询避免 N+1
        List<Long> merchantIds = productMap.values().stream()
                .map(Product::getMerchantId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> merchantNameMap = new HashMap<>();
        if (!merchantIds.isEmpty()) {
            for (User merchant : userMapper.selectBatchIds(merchantIds)) {
                merchantNameMap.put(merchant.getId(),
                        merchant.getNickname() == null || merchant.getNickname().isBlank()
                                ? merchant.getUsername()
                                : merchant.getNickname());
            }
        }

        List<CartItemVO> voList = new ArrayList<>(items.size());
        int totalQuantity = 0;
        int invalidCount = 0;
        boolean allSelected = true;
        BigDecimal estimatedTotal = BigDecimal.ZERO;

        for (CartItem item : items) {
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            totalQuantity += quantity;
            boolean selected = item.getSelected() != null && item.getSelected() == 1;
            if (!selected) {
                allSelected = false;
            }

            Product product = productMap.get(item.getProductId());
            ProductAvailability availability = availabilityResolver.describe(product, quantity);

            CartItemVO vo = new CartItemVO();
            vo.setId(item.getId());
            vo.setProductId(item.getProductId());
            vo.setProductName(product == null ? "商品已删除" : product.getName());
            vo.setMainImage(product == null ? "" : product.getMainImage());
            Long merchantId = product == null ? null : product.getMerchantId();
            vo.setMerchantId(merchantId);
            vo.setMerchantName(merchantId == null
                    ? "平台自营"
                    : merchantNameMap.getOrDefault(merchantId, "商家已注销"));
            vo.setPrice(product == null ? null : product.getPrice());
            vo.setQuantity(quantity);
            vo.setStock(product == null ? 0 : product.getStock());
            vo.setSelected(selected);
            vo.setAvailable(availability.isAvailable());
            vo.setInvalidReason(availability.getReason());

            BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
            if (availability.isAvailable() && product != null && product.getPrice() != null) {
                subtotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
                if (selected) {
                    estimatedTotal = estimatedTotal.add(subtotal);
                }
            } else {
                invalidCount++;
            }
            vo.setSubtotal(subtotal);
            voList.add(vo);
        }

        CartVO result = new CartVO();
        result.setItems(voList);
        result.setTotalQuantity(totalQuantity);
        result.setEstimatedTotal(estimatedTotal.setScale(2, java.math.RoundingMode.HALF_UP));
        result.setInvalidCount(invalidCount);
        result.setAllSelected(allSelected);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(CartAddDTO dto) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        int quantity = dto.getQuantity();

        // 添加时必须是当前可购买商品；库存不足直接拒绝，不先写入购物车再提示
        ProductAvailability availability = availabilityResolver.describe(dto.getProductId(), quantity);
        if (!availability.isAvailable()) {
            throw new BusinessException(ResultCode.CONFLICT, availability.getReason());
        }
        Product product = availability.getProduct();
        int stock = Objects.requireNonNull(product.getStock());

        // 单条原子 upsert：既防重复添加，也避免"insert 失败后 update"在高并发下死锁
        int rows = cartItemMapper.upsertQuantity(userId, dto.getProductId(), quantity, stock);
        if (rows == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "购物车中该商品数量已超过可售库存");
        }
        CartItem item = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getProductId, dto.getProductId()));
        return item == null ? null : item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long itemId, CartUpdateDTO dto) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        CartItem item = requireMyItem(userId, itemId);

        if (dto.getQuantity() != null) {
            Product product = productMapper.selectById(item.getProductId());
            if (product == null) {
                throw new BusinessException(ResultCode.CONFLICT, "商品已失效，请从购物车移除");
            }
            int stock = product.getStock() == null ? 0 : product.getStock();
            if (dto.getQuantity() > stock) {
                throw new BusinessException(ResultCode.CONFLICT,
                        "库存不足，当前仅剩 " + stock + " 件");
            }
            item.setQuantity(dto.getQuantity());
        }
        if (dto.getSelected() != null) {
            item.setSelected(dto.getSelected() ? 1 : 0);
        }
        cartItemMapper.updateById(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long itemId) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        requireMyItem(userId, itemId);
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, itemId)
                .eq(CartItem::getUserId, userId));
    }

    /** 他人条目和不存在条目统一返回 404，避免通过错误码探测他人购物车结构 */
    private CartItem requireMyItem(Long userId, Long itemId) {
        CartItem item = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, itemId)
                .eq(CartItem::getUserId, userId));
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "购物车条目不存在");
        }
        return item;
    }
}
