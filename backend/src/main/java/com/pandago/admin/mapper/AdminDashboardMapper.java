package com.pandago.admin.mapper;

import com.pandago.admin.vo.AdminDashboardVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 平台工作台统计 Mapper。
 *
 * <p>不接收任何前端传入的用户 ID 或商家 ID；统计范围固定为全平台。
 * 指标用三条聚合 SQL 完成，最近订单与待审核商品各一条 LIMIT 查询，避免 N+1。</p>
 */
@Mapper
public interface AdminDashboardMapper {

    /** 用户指标：买家数 / 全部账号数 / 今日新增买家 */
    Map<String, Object> selectUserStats();

    /** 商品指标：总数 / 上架数 / 待审核数 */
    Map<String, Object> selectProductStats();

    /** 订单指标：总数 / 待发货 / 已完成 / 今日新增 / 累计交易额 */
    Map<String, Object> selectOrderStats();

    /** 最近订单（含脱敏买家与首件商品快照） */
    List<AdminDashboardVO.RecentOrderVO> selectRecentOrders(@Param("limit") int limit);

    /** 最近待审核商品（含商家名称） */
    List<AdminDashboardVO.PendingProductVO> selectPendingReviewProducts(@Param("limit") int limit);
}
