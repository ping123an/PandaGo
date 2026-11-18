package com.pandago.cart.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车条目，与 cart_item 表一一对应。
 *
 * <p>刻意不保存 price 字段：购物车展示价必须实时从 product 读取，
 * 把价格冗余在购物车里容易导致"展示价 = 成交价"的错误认知（AGENTS 6.3）。
 * 删除条目使用物理删除，避免逻辑删除的旧行占用 (user_id, product_id) 唯一索引。</p>
 */
@Data
@TableName("cart_item")
public class CartItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long productId;

    private Integer quantity;

    /** 1 勾选 / 0 未勾选 */
    private Integer selected;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
