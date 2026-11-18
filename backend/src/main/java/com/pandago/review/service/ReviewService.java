package com.pandago.review.service;

import com.pandago.common.api.PageResult;
import com.pandago.review.dto.ReviewCreateDTO;
import com.pandago.review.vo.ReviewVO;

import java.util.List;

/** 商品评价业务（阶段 8）。 */
public interface ReviewService {

    /** 对已完成订单中的商品评价；返回评价 ID */
    Long create(ReviewCreateDTO dto);

    /** 商品评价列表（公开，游客可看） */
    List<ReviewVO> listByProduct(Long productId);

    /** 我的评价分页 */
    PageResult<ReviewVO> myReviews(long pageNum, long pageSize);
}
