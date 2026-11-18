package com.pandago.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pandago.cart.entity.CartItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /**
     * 原子 upsert：同一用户同一商品只保留一行，重复添加时合并数量。
     *
     * <p>用一条 INSERT ... ON DUPLICATE KEY UPDATE 完成"新增或累加"，
     * 由唯一索引 uk_cart_user_product 和 InnoDB 行锁保证并发安全。
     * 累加值带库存上限条件，超出库存时不修改行。</p>
     *
     * @return 1 表示新增，2 表示数量累加成功，0 表示超出库存未修改
     */
    int upsertQuantity(@Param("userId") Long userId,
                       @Param("productId") Long productId,
                       @Param("quantity") Integer quantity,
                       @Param("stock") Integer stock);
}
