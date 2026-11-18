package com.pandago.merchant.mapper;

import com.pandago.merchant.vo.MerchantDashboardVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 商家统计 Mapper（工作台聚合查询）。
 *
 * <p>所有查询都以 merchantId 为强制条件，且该值只来自 SecurityContext，
 * 不接受前端传参。用聚合 SQL 一次算出商品与订单指标，避免逐条查询 N+1。</p>
 */
@Mapper
public interface MerchantDashboardMapper {

    /** 按商家聚合商品指标：总数 / 上架数 */
    Map<String, Object> selectProductStats(@Param("merchantId") Long merchantId);

    /** 按商家聚合订单指标：待发货数 / 已完成数 / 销售额（口径见 XML 注释） */
    Map<String, Object> selectOrderStats(@Param("merchantId") Long merchantId);

    /** 当前商家最近订单（含买家脱敏信息） */
    List<MerchantDashboardVO.RecentOrderVO> selectRecentOrders(
            @Param("merchantId") Long merchantId, @Param("limit") int limit);

    /** 当前商家最近创建的商品 */
    List<MerchantDashboardVO.RecentProductVO> selectRecentProducts(
            @Param("merchantId") Long merchantId, @Param("limit") int limit);
}
