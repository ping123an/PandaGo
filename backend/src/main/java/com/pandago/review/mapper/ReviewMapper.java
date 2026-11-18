package com.pandago.review.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pandago.review.entity.Review;
import com.pandago.review.vo.ReviewVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /** 商品评价列表：按时间倒序，关联 user 取昵称展示 */
    List<ReviewVO> selectByProduct(@Param("productId") Long productId);
}
