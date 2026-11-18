package com.pandago.favorite.controller;

import com.pandago.common.api.Result;
import com.pandago.favorite.dto.FavoriteAddDTO;
import com.pandago.favorite.service.FavoriteService;
import com.pandago.favorite.vo.FavoriteVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 商品收藏接口（阶段 5，全部需要登录，身份来自 SecurityContext）。 */
@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** 添加收藏；重复收藏返回 409 */
    @PostMapping
    public Result<Long> add(@Valid @RequestBody FavoriteAddDTO dto) {
        return Result.ok(favoriteService.add(dto));
    }

    /** 按商品 id 取消我的收藏 */
    @DeleteMapping("/{productId}")
    public Result<Void> remove(@PathVariable Long productId) {
        favoriteService.removeByProduct(productId);
        return Result.ok();
    }

    /** 我的收藏列表 */
    @GetMapping
    public Result<List<FavoriteVO>> list() {
        return Result.ok(favoriteService.listMyFavorites());
    }

    /** 指定商品是否已收藏，供商品详情/卡片按钮回显 */
    @GetMapping("/check")
    public Result<Boolean> check(@RequestParam Long productId) {
        return Result.ok(favoriteService.isFavorited(productId));
    }
}
