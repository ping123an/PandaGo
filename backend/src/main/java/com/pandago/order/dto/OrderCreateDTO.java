package com.pandago.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建订单请求（阶段 6）。
 *
 * <p>刻意只声明三类必要信息：
 * 1. 结算来源：cartItemIds（购物车条目 ID）或 items（商品 ID + 数量），二选一；
 * 2. addressId：收货地址 ID；
 * 3. remark：可选备注。
 *
 * <p><b>绝不声明</b> price / totalAmount / payAmount / userId / merchantId 等字段。
 * 即使请求 JSON 里塞入这些字段，也会被 Jackson 忽略（本项目未开启 FAIL_ON_UNKNOWN_PROPERTIES），
 * 订单金额、用户、商家一律由后端根据数据库和 SecurityContext 决定。这是"不能信任前端价格"的第一道防线。</p>
 */
@Data
public class OrderCreateDTO {

    /** 购物车条目 ID 列表（结算来源之一）；数量取自购物车，价格仍从商品表实时读取 */
    private List<@NotNull(message = "购物车条目ID不能为空") Long> cartItemIds;

    /** 直接购买商品列表（结算来源之一），与 cartItemIds 二选一 */
    @Valid
    private List<OrderItemInput> items;

    /** 收货地址 ID，必须属于当前登录用户 */
    @NotNull(message = "请选择收货地址")
    private Long addressId;

    /**
     * 结算批次号（幂等键）：同一次提交拆出的多张订单共享此编号。
     * 由前端在点击提交时生成 UUID；可不传，后端会自动生成。
     * 与 merchant_id 组成唯一索引，重复提交会命中唯一键冲突并整体回滚，防止重复下单/重复扣库存。
     */
    @Size(max = 64, message = "结算批次号过长")
    private String checkoutGroupId;

    /**
     * 订单来源（V8）：CART=购物车结算，DIRECT=立即购买。
     * 仅作来源标记，不影响价格/商家/库存校验；缺省按 CART，兼容旧客户端。
     */
    @Size(max = 16, message = "订单来源过长")
    private String source;

    /** 订单备注 */
    @Size(max = 200, message = "订单备注最长 200 个字符")
    private String remark;

    /** 直接购买模式的单行输入：只接受商品 ID 与数量，不接受任何价格字段 */
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

    /** 规范化后的下单行，供 Service 内部使用，不暴露给前端 */
    @Data
    public static class OrderLine {
        private Long productId;
        private Integer quantity;
        /** 来源购物车条目 ID；直接购买模式为 null */
        private Long cartItemId;

        public OrderLine(Long productId, Integer quantity, Long cartItemId) {
            this.productId = productId;
            this.quantity = quantity;
            this.cartItemId = cartItemId;
        }
    }
}
