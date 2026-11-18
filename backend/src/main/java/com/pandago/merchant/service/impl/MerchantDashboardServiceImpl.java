package com.pandago.merchant.service.impl;

import com.pandago.common.enums.RoleCode;
import com.pandago.merchant.entity.MerchantProfile;
import com.pandago.merchant.mapper.MerchantDashboardMapper;
import com.pandago.merchant.mapper.MerchantProfileMapper;
import com.pandago.merchant.service.MerchantDashboardService;
import com.pandago.merchant.vo.MerchantDashboardVO;
import com.pandago.security.SecurityUtils;
import com.pandago.user.entity.User;
import com.pandago.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商家工作台统计（V9）。
 *
 * <p>安全要点：merchantId 只来自 SecurityContext，先校验 MERCHANT 角色再查询；
 * 所有聚合 SQL 都以该 id 为强制 WHERE 条件，前端传入的 merchantId 一律忽略。
 * 商品与订单分别用一条聚合 SQL 计算，最近订单/商品各一条，共 4 条查询，避免 N+1。</p>
 */
@Service
@RequiredArgsConstructor
public class MerchantDashboardServiceImpl implements MerchantDashboardService {

    private static final int RECENT_LIMIT = 5;

    private final MerchantDashboardMapper dashboardMapper;
    private final MerchantProfileMapper profileMapper;
    private final UserMapper userMapper;

    @Override
    public MerchantDashboardVO dashboard() {
        Long merchantId = currentMerchantId();

        Map<String, Object> productStats = dashboardMapper.selectProductStats(merchantId);
        Map<String, Object> orderStats = dashboardMapper.selectOrderStats(merchantId);

        MerchantDashboardVO vo = new MerchantDashboardVO();
        // 店铺名称取自店铺资料；尚未创建资料的商家回退到用户昵称，保证页面不空白
        MerchantProfile profile = profileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MerchantProfile>()
                        .eq(MerchantProfile::getUserId, merchantId));
        if (profile != null) {
            vo.setStoreName(profile.getStoreName());
            vo.setStoreStatus(profile.getStatus());
        } else {
            User user = userMapper.selectById(merchantId);
            vo.setStoreName(user == null ? "我的店铺" : user.getNickname());
            vo.setStoreStatus(MerchantProfile.STATUS_NORMAL);
        }
        vo.setStoreStatusDescription(MerchantProfile.STATUS_NORMAL.equals(vo.getStoreStatus())
                ? "正常经营" : "已暂停");

        vo.setProductTotal(toLong(productStats, "productTotal"));
        vo.setProductOnSale(toLong(productStats, "productOnSale"));
        vo.setPendingShipmentOrders(toLong(orderStats, "pendingShipmentOrders"));
        vo.setCompletedOrders(toLong(orderStats, "completedOrders"));
        Object sales = orderStats == null ? null : orderStats.get("salesAmount");
        vo.setSalesAmount(sales == null ? BigDecimal.ZERO.setScale(2)
                : new BigDecimal(sales.toString()).setScale(2, java.math.RoundingMode.HALF_UP));

        List<MerchantDashboardVO.RecentOrderVO> orders = dashboardMapper.selectRecentOrders(merchantId, RECENT_LIMIT);
        List<MerchantDashboardVO.RecentProductVO> products = dashboardMapper.selectRecentProducts(merchantId, RECENT_LIMIT);
        vo.setRecentOrders(orders == null ? List.of() : orders);
        vo.setRecentProducts(products == null ? List.of() : products);
        return vo;
    }

    /** 角色校验 + 当前商家 ID，必须在任何归属查询前调用 */
    private Long currentMerchantId() {
        SecurityUtils.requireRole(RoleCode.MERCHANT);
        return SecurityUtils.requireCurrentUser().getUserId();
    }

    /** MyBatis 聚合返回类型为 Map，COUNT 在无行时仍返回 0；这里做空值兜底 */
    private long toLong(Map<String, Object> map, String key) {
        if (map == null || map.get(key) == null) return 0L;
        return ((Number) map.get(key)).longValue();
    }
}
