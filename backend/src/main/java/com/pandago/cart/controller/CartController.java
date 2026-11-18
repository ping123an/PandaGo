package com.pandago.cart.controller;

import com.pandago.cart.dto.CartAddDTO;
import com.pandago.cart.dto.CartUpdateDTO;
import com.pandago.cart.service.CartService;
import com.pandago.cart.vo.CartVO;
import com.pandago.common.api.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 购物车接口（阶段 5，全部需要登录）。
 *
 * <p>路径不加 userId：当前用户由 SecurityContext 决定，Controller 不接收任何"身份参数"。
 * @Valid 拦截 quantity<=0 等基础参数错误，业务规则留给 Service。</p>
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /** 查询我的购物车 */
    @GetMapping
    public Result<CartVO> getMyCart() {
        return Result.ok(cartService.getMyCart());
    }

    /** 添加商品，返回购物车条目 id */
    @PostMapping("/items")
    public Result<Long> add(@Valid @RequestBody CartAddDTO dto) {
        return Result.ok(cartService.add(dto));
    }

    /** 修改数量或勾选状态 */
    @PutMapping("/items/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody CartUpdateDTO dto) {
        cartService.update(id, dto);
        return Result.ok();
    }

    /** 删除条目 */
    @DeleteMapping("/items/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        cartService.delete(id);
        return Result.ok();
    }
}
