package com.pandago.order.scheduler;

import com.pandago.order.service.OrderTimeoutScanService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 超时订单定时任务入口（阶段 9）。
 *
 * <p>{@code @Scheduled(fixedDelayString)}：上一轮结束后再等待 fixed-delay 毫秒执行下一轮，
 * 不会因处理时间过长而堆叠。可通过 {@code pandago.order.timeout.schedule-enabled=false}
 * 关闭（测试环境默认关闭，改为手动调用扫描服务验证）。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pandago.order.timeout.schedule-enabled", havingValue = "true", matchIfMissing = true)
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

    private final OrderTimeoutScanService scanService;

    @Scheduled(
            initialDelayString = "${pandago.order.timeout.initial-delay-ms:60000}",
            fixedDelayString = "${pandago.order.timeout.fixed-delay-ms:60000}")
    public void closeTimedOutOrders() {
        try {
            scanService.scanAndClose();
        } catch (Exception e) {
            // 兜底：扫描本身异常也不能让定时任务容器停止调度
            log.error("超时订单扫描任务异常", e);
        }
    }
}
