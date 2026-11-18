package com.pandago.order.service.impl;

import com.pandago.common.enums.OrderStatus;
import com.pandago.order.mapper.OrderMapper;
import com.pandago.order.service.OrderTimeoutCloseService;
import com.pandago.order.service.OrderTimeoutScanService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超时订单扫描（阶段 9）。
 *
 * <p>流程：按配置的 timeoutMinutes 算出截止时间 cutoff，分批查询 created_at &lt;= cutoff
 * 的待支付订单 ID，逐单调用独立事务的 {@link OrderTimeoutCloseService#closeOne}。
 * 每单独立事务，单个失败不影响其他订单；失败只记 WARN，不中断整批。</p>
 *
 * <p>日志策略：无超时订单时完全静默（DEBUG），只有实际关闭了订单才输出 INFO 汇总，
 * 避免每 60 秒刷一条无用日志（AGENTS 6.9）。</p>
 */
@Service
@RequiredArgsConstructor
public class OrderTimeoutScanServiceImpl implements OrderTimeoutScanService {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScanServiceImpl.class);

    private final OrderMapper orderMapper;
    private final OrderTimeoutCloseService closeService;

    @Value("${pandago.order.timeout.minutes:30}")
    private long timeoutMinutes;

    @Value("${pandago.order.timeout.batch-size:100}")
    private int batchSize;

    @Override
    public int scanAndClose() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<Long> orderIds = orderMapper.selectTimedOutOrderIds(
                OrderStatus.PENDING_PAYMENT.getValue(), cutoff, batchSize);
        if (orderIds.isEmpty()) {
            log.debug("超时订单扫描：无待关闭订单，cutoff={}", cutoff);
            return 0;
        }

        int closed = 0;
        for (Long orderId : orderIds) {
            try {
                if (closeService.closeOne(orderId, cutoff)) {
                    closed++;
                }
            } catch (Exception e) {
                // 单笔失败不阻断整批；事务已回滚，订单与库存保持一致
                log.warn("超时订单关闭失败，已跳过该单: orderId={}, reason={}", orderId, e.getMessage());
            }
        }
        if (closed > 0) {
            log.info("超时订单扫描完成：命中 {} 单，成功关闭 {} 单，cutoff={}", orderIds.size(), closed, cutoff);
        }
        return closed;
    }
}
