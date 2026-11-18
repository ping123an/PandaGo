package com.pandago.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** 商品上下架请求，状态值限定为枚举值。 */
@Data
public class ProductStatusDTO {

    @NotBlank(message = "状态不能为空")
    @Pattern(regexp = "ON_SALE|OFF_SALE", message = "状态只能为 ON_SALE 或 OFF_SALE")
    private String status;
}
