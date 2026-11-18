package com.pandago.merchant.vo;

import com.pandago.common.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 商家工作台统计 VO：全部来自当前登录商家的数据库真实数据。 */
@Data
public class MerchantDashboardVO {

    private String storeName;

    private String storeStatus;

    private String storeStatusDescription;

    /** 商品总数（未删除） */
    private long productTotal;

    /** 上架商品数（ON_SALE + APPROVED） */
    private long productOnSale;

    /** 待发货订单数 */
    private long pendingShipmentOrders;

    /** 已完成订单数 */
    private long completedOrders;

    /** 销售额：仅已支付且未取消的订单金额合计 */
    private BigDecimal salesAmount;

    private List<RecentOrderVO> recentOrders = new ArrayList<>();

    private List<RecentProductVO> recentProducts = new ArrayList<>();

    @Data
    public static class RecentOrderVO {
        private Long id;
        private String orderNo;
        private BigDecimal totalAmount;
        private String status;
        private LocalDateTime createdAt;
        /** 买家收货人姓名（订单快照） */
        private String buyerName;
        /** 脱敏手机号：138****0000 */
        private String buyerPhoneMasked;

        public String getStatusDescription() {
            return OrderStatus.describe(status);
        }
    }

    @Data
    public static class RecentProductVO {
        private Long id;
        private String name;
        private String mainImage;
        private BigDecimal price;
        private Integer stock;
        private String status;
        private String auditStatus;
        private Integer sales;
        private LocalDateTime createdAt;
    }
}
