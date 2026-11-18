package com.pandago.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 加入购物车请求。userId 不可由客户端传入，服务端从登录身份读取。 */
@Data
public class CartAddDTO {

    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量必须大于 0")
    @Max(value = 999, message = "单次添加数量不能超过 999")
    private Integer quantity;
}
