package com.pandago.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 订单预览请求（立即购买 / 购物车结算共用）。
 *
 * <p>只携带"购买意向"：来源、商品 ID + 数量、地址 ID。
 * 价格、商家、库存、上架状态一律由后端重新查询，前端传值不参与任何金额计算。</p>
 */
@Data
public class OrderPreviewDTO {

    /** CART=购物车结算，DIRECT=立即购买；缺省 CART */
    @Pattern(regexp = "CART|DIRECT", message = "订单来源只能为 CART 或 DIRECT")
    private String source;

    @NotNull(message = "请选择收货地址")
    private Long addressId;

    @Valid
    @NotEmpty(message = "没有需要预览的商品")
    @Size(max = 50, message = "一次最多预览 50 种商品")
    private List<OrderItemInput> items;

    /** 预览行：只接受商品 ID 与数量 */
    @Data
    public static class OrderItemInput {

        @NotNull(message = "商品ID不能为空")
        @Min(value = 1, message = "商品ID必须大于 0")
        private Long productId;

        @NotNull(message = "购买数量不能为空")
        @Min(value = 1, message = "购买数量必须大于 0")
        @Max(value = 999, message = "购买数量不能超过 999")
        private Integer quantity;
    }
}
