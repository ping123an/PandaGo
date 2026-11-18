package com.pandago.review.controller;

import com.pandago.common.api.PageResult;
import com.pandago.common.api.Result;
import com.pandago.review.dto.ReviewCreateDTO;
import com.pandago.review.service.ReviewService;
import com.pandago.review.vo.ReviewVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品评价接口（阶段 8）。
 * 商品评价列表公开（游客可看），写操作需登录，归属与完成状态由后端校验。
 */
@Validated
@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /** 对已完成订单中的商品评价 */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody ReviewCreateDTO dto) {
        return Result.ok(reviewService.create(dto));
    }

    /** 商品评价列表（公开） */
    @GetMapping("/products/{productId}")
    public Result<List<ReviewVO>> listByProduct(@PathVariable Long productId) {
        return Result.ok(reviewService.listByProduct(productId));
    }

    /** 我的评价分页 */
    @GetMapping("/mine")
    public Result<PageResult<ReviewVO>> myReviews(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(reviewService.myReviews(pageNum, pageSize));
    }
}
