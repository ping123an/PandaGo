package com.pandago.admin.service.impl;

import com.pandago.admin.mapper.AdminDashboardMapper;
import com.pandago.admin.service.AdminDashboardService;
import com.pandago.admin.vo.AdminDashboardVO;
import com.pandago.common.enums.RoleCode;
import com.pandago.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 平台工作台统计实现。
 *
 * <p>安全：方法入口先校验 ADMIN 角色，接口不接收任何 ID 参数，
 * 统计范围全部由 Mapper 固定为全平台，前端无法传入管理员 ID 越权。</p>
 *
 * <p>性能：5 条 SQL 完成全部数据——3 条聚合 + 2 条带 LIMIT 的明细查询，
 * 不在 Java 中逐条回查订单或商品，避免 N+1。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int RECENT_LIMIT = 5;

    private final AdminDashboardMapper dashboardMapper;

    @Override
    public AdminDashboardVO dashboard() {
        // 业务层第二道防线：URL 规则与方法注解之外再次确认角色
        SecurityUtils.requireRole(RoleCode.ADMIN);
        SecurityUtils.requireCurrentUser();

        Map<String, Object> userStats = dashboardMapper.selectUserStats();
        Map<String, Object> productStats = dashboardMapper.selectProductStats();
        Map<String, Object> orderStats = dashboardMapper.selectOrderStats();

        AdminDashboardVO vo = new AdminDashboardVO();
        vo.setUserTotal(toLong(userStats, "userTotal"));
        vo.setAccountTotal(toLong(userStats, "accountTotal"));
        vo.setTodayNewUsers(toLong(userStats, "todayNewUsers"));
        vo.setProductTotal(toLong(productStats, "productTotal"));
        vo.setProductOnSale(toLong(productStats, "productOnSale"));
        vo.setPendingReviewProducts(toLong(productStats, "pendingReviewProducts"));
        vo.setOrderTotal(toLong(orderStats, "orderTotal"));
        vo.setPendingShipmentOrders(toLong(orderStats, "pendingShipmentOrders"));
        vo.setCompletedOrders(toLong(orderStats, "completedOrders"));
        vo.setTodayNewOrders(toLong(orderStats, "todayNewOrders"));
        vo.setTotalSalesAmount(toMoney(orderStats, "totalSalesAmount"));

        List<AdminDashboardVO.RecentOrderVO> orders = dashboardMapper.selectRecentOrders(RECENT_LIMIT);
        List<AdminDashboardVO.PendingProductVO> pendingProducts =
                dashboardMapper.selectPendingReviewProducts(RECENT_LIMIT);
        vo.setRecentOrders(orders == null ? List.of() : orders);
        if (pendingProducts != null) {
            // 平台演示商品 merchant_id=0 没有用户记录，兜底文案放在 Java 层
            for (AdminDashboardVO.PendingProductVO item : pendingProducts) {
                if (item.getMerchantName() == null || item.getMerchantName().isBlank()) {
                    item.setMerchantName("平台自营");
                }
            }
        }
        vo.setPendingReviewProductList(pendingProducts == null ? List.of() : pendingProducts);
        return vo;
    }

    /** 聚合查询可能为 null（空表），统一兜底为 0，避免页面显示空白或 NPE */
    private long toLong(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) return 0L;
        return ((Number) map.get(key)).longValue();
    }

    /** 金额统一保留两位小数，null 视为 0 */
    private BigDecimal toMoney(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) return BigDecimal.ZERO.setScale(2);
        return new BigDecimal(map.get(key).toString()).setScale(2, RoundingMode.HALF_UP);
    }
}
