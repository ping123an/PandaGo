package com.pandago.admin.vo;

import com.pandago.common.enums.OrderStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 平台管理工作台 VO：所有字段均由聚合查询或受限明细查询得到，不接收前端传入的统计值。
 *
 * <p>用户数按角色区分：userTotal 是买家（USER 角色）账号数，accountTotal 是全部账号数；
 * 两者都按 user.id 去重，避免多角色用户被重复计数。</p>
 */
@Data
public class AdminDashboardVO {

    /** 买家账号数（拥有 USER 角色的未删除用户） */
    private long userTotal;

    /** 平台全部账号数（含商家、管理员） */
    private long accountTotal;

    /** 今日新增买家账号数 */
    private long todayNewUsers;

    /** 全平台商品数（未删除） */
    private long productTotal;

    /** 上架商品数：ON_SALE + APPROVED + 未删除 */
    private long productOnSale;

    /** 待审核商品数 */
    private long pendingReviewProducts;

    /** 全平台订单数 */
    private long orderTotal;

    /** 待发货订单数 */
    private long pendingShipmentOrders;

    /** 已完成订单数 */
    private long completedOrders;

    /** 今日新增订单数 */
    private long todayNewOrders;

    /**
     * 累计交易金额（模拟支付）。口径与项目支付状态一致：
     * 仅统计有成功支付记录且订单状态未取消的订单，排除待支付订单。
     */
    private BigDecimal totalSalesAmount;

    /** 最近订单（最多 5 条，按创建时间倒序） */
    private List<RecentOrderVO> recentOrders = new ArrayList<>();

    /** 最近待审核商品（最多 5 条，按提交时间倒序） */
    private List<PendingProductVO> pendingReviewProductList = new ArrayList<>();

    @Data
    public static class RecentOrderVO {
        private Long id;
        private String orderNo;
        private BigDecimal totalAmount;
        private String status;
        private LocalDateTime createdAt;
        /** 订单快照中的收货人姓名 */
        private String buyerName;
        /** 脱敏手机号：138****0000 */
        private String buyerPhoneMasked;
        /** 订单快照中的首件商品名称 */
        private String firstProductName;
        /** 订单明细件数 */
        private Integer itemCount;

        public String getStatusDescription() {
            return OrderStatus.describe(status);
        }
    }

    @Data
    public static class PendingProductVO {
        private Long id;
        private String name;
        private String mainImage;
        private BigDecimal price;
        /** 商家店铺名；无资料时回退昵称/用户名，平台商品显示“平台自营” */
        private String merchantName;
        /** 商品创建时间，即提交审核时间 */
        private LocalDateTime createdAt;
        private String auditStatus;
    }
}
