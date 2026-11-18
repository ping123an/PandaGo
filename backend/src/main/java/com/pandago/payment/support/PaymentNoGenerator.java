package com.pandago.payment.support;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/** 后端支付流水号生成器：时间戳 + 随机数；唯一性由 uk_payment_no 索引兜底。 */
@Component
public class PaymentNoGenerator {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String next() {
        String rand = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return "PAY" + LocalDateTime.now().format(TS) + rand;
    }
}
