package com.pandago.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评价实体（阶段 8）。
 *
 * <p>uk_review_order_item（order_item_id 唯一）保证同一订单商品只能评价一次；
 * 并发下由数据库唯一索引兜底，业务层先做友好校验。</p>
 */
@Data
@TableName("review")
public class Review {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long orderId;

    private Long orderItemId;

    private Long productId;

    /** 商品名称快照 */
    private String productName;

    /** 评分 1~5 */
    private Integer rating;

    private String content;

    private LocalDateTime createdAt;
}
