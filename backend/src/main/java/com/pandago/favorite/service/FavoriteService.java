package com.pandago.favorite.service;

import com.pandago.favorite.dto.FavoriteAddDTO;
import com.pandago.favorite.vo.FavoriteVO;

import java.util.List;

/** 当前登录用户的收藏业务；所有操作强制按登录用户隔离。 */
public interface FavoriteService {

    /** 添加收藏，返回收藏 id；重复收藏抛出冲突 */
    Long add(FavoriteAddDTO dto);

    /** 按商品 id 取消我的收藏 */
    void removeByProduct(Long productId);

    /** 我的收藏列表（含商品状态） */
    List<FavoriteVO> listMyFavorites();

    /** 指定商品是否已收藏，供前端收藏按钮回显状态 */
    boolean isFavorited(Long productId);
}
