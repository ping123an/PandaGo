package com.pandago.order.support;

import com.pandago.order.dto.OrderCreateDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 事务内写入上下文：由校验阶段生成，包含所有快照数据与金额。
 *
 * <p>把"校验结果"封装成不可变风格的数据对象在方法间传递，
 * 避免在事务方法里重新查库或依赖前端参数，也便于单元测试。</p>
 */
@Data
public class OrderDraft {
    private Long userId;
    private Long merchantId;
    /** 结算批次号：同一次购物车提交拆出的多张订单共享 */
    private String checkoutGroupId;
    /** 订单来源：CART / DIRECT */
    private String source;
    private String remark;
    private BigDecimal totalAmount;

    private String receiverName;
    private String receiverPhone;
    private String receiverProvince;
    private String receiverCity;
    private String receiverDistrict;
    private String receiverDetailAddress;

    /** 下单行：商品快照 + 数量 + 来源购物车条目 ID */
    private List<DraftLine> lines;

    @Data
    public static class DraftLine {
        private Long productId;
        private String productName;
        private String productImage;
        private BigDecimal unitPrice;
        private Integer quantity;
        private BigDecimal subtotal;
        /** 来自购物车则为条目 ID，直接购买为 null（结算后需清理购物车） */
        private Long cartItemId;

        public DraftLine(Long productId, Integer quantity, Long cartItemId) {
            this.productId = productId;
            this.quantity = quantity;
            this.cartItemId = cartItemId;
        }
    }
}
