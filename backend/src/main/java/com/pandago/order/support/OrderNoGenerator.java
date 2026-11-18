package com.pandago.order.support;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** 后端订单号生成器：时间戳 + 随机数，唯一性由 uk_orders_no 索引兜底。 */
@Component
public class OrderNoGenerator {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String next() {
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "PO" + LocalDateTime.now().format(TS) + rand;
    }
}
