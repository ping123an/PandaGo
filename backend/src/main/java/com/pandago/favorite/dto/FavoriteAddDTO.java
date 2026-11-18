package com.pandago.favorite.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 新增收藏请求；不接受 userId。 */
@Data
public class FavoriteAddDTO {

    @NotNull(message = "商品ID不能为空")
    private Long productId;
}
