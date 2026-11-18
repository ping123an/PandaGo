package com.pandago.order.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单预览返回：确认订单页展示的权威数据。
 *
 * <p>所有字段由后端基于数据库实时数据计算，前端不得自行改写。
 * 立即购买与购物车结算共用本结构，仅 source 与商品行来源不同。</p>
 */
@Data
public class OrderPreviewVO {

    /** CART / DIRECT */
    private String source;

    private List<PreviewAddress> address;

    private List<PreviewItem> items = new ArrayList<>();

    /** 按商家分组的商品行，便于确认页按店铺展示 */
    private List<PreviewMerchantGroup> merchantGroups = new ArrayList<>();

    /** 商品总金额 */
    private BigDecimal totalAmount;

    /** 优惠金额：当前无优惠系统，固定 0 */
    private BigDecimal discountAmount;

    /** 运费：当前无运费系统，固定 0 */
    private BigDecimal freightAmount;

    /** 应付金额 = 商品总金额 - 优惠 + 运费 */
    private BigDecimal payableAmount;

    /** 预计拆单数（按商家数量） */
    private Integer merchantCount;

    @Data
    public static class PreviewAddress {
        private Long id;
        private String receiverName;
        private String phone;
        private String province;
        private String city;
        private String district;
        private String detailAddress;
        private Boolean isDefault;
    }

    @Data
    public static class PreviewItem {
        private Long productId;
        private String productName;
        private String productImage;
        /** 单规格商品：默认规格文案；项目暂无 SKU 体系 */
        private String specText;
        private BigDecimal unitPrice;
        private Integer quantity;
        private BigDecimal subtotal;
        private Long merchantId;
        private String merchantName;
    }

    @Data
    public static class PreviewMerchantGroup {
        private Long merchantId;
        private String merchantName;
        private List<PreviewItem> items = new ArrayList<>();
        private BigDecimal subtotal;
    }
}
