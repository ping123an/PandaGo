package com.pandago.review.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 商品评价 VO（阶段 8）：评价列表展示，不暴露内部用户 ID 之外的敏感信息。 */
@Data
public class ReviewVO {
    private Long id;
    private Long userId;
    private String username;
    private Long productId;
    private Long orderItemId;
    private Integer rating;
    private String content;
    private LocalDateTime createdAt;
}
