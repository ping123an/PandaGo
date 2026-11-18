package com.pandago.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 修改购物车条目请求。
 *
 * <p>quantity / selected 均为可选：只更新本次明确传入的字段；
 * 两者都不传视为无变化，直接返回成功。数量为 0 或负数由 @Min 拦截为 400。</p>
 */
@Data
public class CartUpdateDTO {

    @Min(value = 1, message = "数量必须大于 0")
    @Max(value = 999, message = "数量不能超过 999")
    private Integer quantity;

    private Boolean selected;
}
