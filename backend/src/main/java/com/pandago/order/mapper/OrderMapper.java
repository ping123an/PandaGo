package com.pandago.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pandago.order.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Orders> {

    /**
     * 订单状态条件流转（阶段 7，用户支付/取消）。
     *
     * <p>from 状态和 userId 都在 WHERE 中，只有当前用户且仍处于源状态的订单才会变更。
     * 受影响行数 0 表示状态已被其他请求改变（已支付/已取消/并发竞争），调用方必须中止后续写操作。</p>
     */
    int transitionStatus(@Param("id") Long id,
                         @Param("userId") Long userId,
                         @Param("from") String from,
                         @Param("to") String to);

    /**
     * 商家发货条件流转（阶段 8）：待发货 -> 待收货。
     *
     * <p>WHERE 同时带 merchant_id 与源状态：商家只能发货自己的订单，且只有仍处于待发货
     * 才能获得变更权。受影响行数 0 表示不是自己的订单、状态不对或并发竞争。</p>
     */
    int shipOrder(@Param("id") Long id,
                  @Param("merchantId") Long merchantId,
                  @Param("from") String from,
                  @Param("to") String to);

    /**
     * 用户确认收货条件流转（阶段 8）：待收货 -> 已完成，并记录收货时间。
     *
     * <p>WHERE 带 user_id 与源状态，重复确认第二次影响 0 行，不产生副作用。</p>
     */
    int confirmReceipt(@Param("id") Long id,
                       @Param("userId") Long userId,
                       @Param("from") String from,
                       @Param("to") String to,
                       @Param("receivedAt") java.time.LocalDateTime receivedAt);

    /**
     * 超时订单条件关闭（阶段 9）：比用户取消多一个 created_at 截止时间条件。
     *
     * <p>WHERE 带 from 状态与 created_at &lt;= cutoff，保证：
     * 1) 只有仍待支付的订单会被关闭（已支付/已取消影响 0 行）；
     * 2) 只有创建时间早于超时阈值的订单会被关闭（未超时影响 0 行）；
     * 3) 定时任务重复扫描时，第一次已把它改成 CANCELLED，第二次影响 0 行，幂等。</p>
     *
     * @return 1 表示本次成功抢到关闭权，调用方必须在此情况下才回补库存；0 表示跳过
     */
    int closeTimeoutOrder(@Param("id") Long id,
                          @Param("from") String from,
                          @Param("to") String to,
                          @Param("cutoff") LocalDateTime cutoff);

    /**
     * 分批查询超时且仍待支付的订单 ID，只取主键，避免一次加载整行与明细。
     * 按 created_at 升序，先处理最老的订单。
     */
    List<Long> selectTimedOutOrderIds(@Param("status") String status,
                                      @Param("cutoff") LocalDateTime cutoff,
                                      @Param("limit") int limit);
}
