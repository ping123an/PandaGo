package com.pandago.cart.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 购物车汇总：条目 + 件数 + 已选商品的预估金额（非成交价）。 */
@Data
public class CartVO {

    private List<CartItemVO> items;

    /** 所有条目数量之和，含未勾选与失效商品 */
    private Integer totalQuantity;

    /** 仅统计"已勾选且可购买"的条目，价格来自商品表实时读取 */
    private BigDecimal estimatedTotal;

    /** 失效条目数（下架 / 未审核 / 售罄 / 库存不足 / 已删除） */
    private Integer invalidCount;

    /** 是否全部勾选；空购物车为 false */
    private Boolean allSelected;
}
