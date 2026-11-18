package com.pandago.cart.service;

import com.pandago.cart.dto.CartAddDTO;
import com.pandago.cart.dto.CartUpdateDTO;
import com.pandago.cart.vo.CartVO;

/** 当前登录用户的购物车业务接口；所有方法都不接收 userId 作为业务参数。 */
public interface CartService {

    /** 查询我的购物车（含实时价格与失效原因） */
    CartVO getMyCart();

    /** 加入购物车；重复添加合并数量 */
    Long add(CartAddDTO dto);

    /** 修改我的条目的数量或选中状态 */
    void update(Long itemId, CartUpdateDTO dto);

    /** 删除我的条目 */
    void delete(Long itemId);
}
