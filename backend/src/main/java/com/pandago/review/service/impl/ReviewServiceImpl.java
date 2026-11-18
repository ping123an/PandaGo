package com.pandago.review.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pandago.common.api.PageResult;
import com.pandago.common.api.ResultCode;
import com.pandago.common.enums.OrderStatus;
import com.pandago.common.exception.BusinessException;
import com.pandago.order.entity.OrderItem;
import com.pandago.order.entity.Orders;
import com.pandago.order.mapper.OrderItemMapper;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.review.dto.ReviewCreateDTO;
import com.pandago.review.entity.Review;
import com.pandago.review.mapper.ReviewMapper;
import com.pandago.review.service.ReviewService;
import com.pandago.review.vo.ReviewVO;
import com.pandago.security.SecurityUtils;
import com.pandago.user.entity.User;
import com.pandago.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品评价业务实现（阶段 8）。
 *
 * <p>规则：只能评价自己已完成订单中的商品、同一订单商品只能评价一次、评分 1~5、内容长度校验。
 * 用户归属由 orderItemId → orderId → orders.userId 反查校验，绝不信任前端传来的 userId/productId。</p>
 *
 * <p>重复评价：先查库给出友好提示；并发下 uk_review_order_item 抛 DuplicateKeyException，
 * 转成 409，不会产生第二条评价。</p>
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderMapper orderMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ReviewCreateDTO dto) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();

        // 1. 订单明细必须存在
        OrderItem item = orderItemMapper.selectById(dto.getOrderItemId());
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单商品不存在");
        }
        // 2. 反查订单：必须属于当前用户
        Orders order = orderMapper.selectById(item.getOrderId());
        if (order == null || !userId.equals(order.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单商品不存在或不属于当前用户");
        }
        // 3. 订单必须已完成
        if (!OrderStatus.COMPLETED.getValue().equals(order.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "订单未完成，不能评价（当前状态：" + OrderStatus.describe(order.getStatus()) + "）");
        }
        // 4. 重复评价友好校验（最终由唯一索引兜底）
        Long exists = reviewMapper.selectCount(new LambdaQueryWrapper<Review>()
                .eq(Review::getOrderItemId, item.getId()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该订单商品已评价，请勿重复评价");
        }

        Review review = new Review();
        review.setUserId(userId);
        review.setOrderId(order.getId());
        review.setOrderItemId(item.getId());
        review.setProductId(item.getProductId());
        review.setProductName(item.getProductName());
        review.setRating(dto.getRating());
        review.setContent(dto.getContent().trim());

        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ResultCode.CONFLICT, "该订单商品已评价，请勿重复评价");
        }
        return review.getId();
    }

    @Override
    public List<ReviewVO> listByProduct(Long productId) {
        if (productId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品ID不能为空");
        }
        return reviewMapper.selectByProduct(productId);
    }

    @Override
    public PageResult<ReviewVO> myReviews(long pageNum, long pageSize) {
        Long userId = SecurityUtils.requireCurrentUser().getUserId();
        IPage<Review> page = reviewMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getCreatedAt));
        List<ReviewVO> vos = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(vos, page.getTotal(), pageNum, pageSize, page.getPages());
    }

    private ReviewVO toVO(Review review) {
        ReviewVO vo = new ReviewVO();
        vo.setId(review.getId());
        vo.setUserId(review.getUserId());
        User user = userMapper.selectById(review.getUserId());
        if (user != null) {
            vo.setUsername(user.getNickname() == null || user.getNickname().isBlank()
                    ? user.getUsername() : user.getNickname());
        }
        vo.setProductId(review.getProductId());
        vo.setOrderItemId(review.getOrderItemId());
        vo.setRating(review.getRating());
        vo.setContent(review.getContent());
        vo.setCreatedAt(review.getCreatedAt());
        return vo;
    }
}
