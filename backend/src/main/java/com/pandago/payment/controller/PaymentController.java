package com.pandago.payment.controller;

import com.pandago.common.api.Result;
import com.pandago.payment.dto.MockPaymentDTO;
import com.pandago.payment.service.PaymentService;
import com.pandago.payment.vo.PaymentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 模拟支付接口（阶段 7）：不接真实第三方支付，不接收金额。 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    private final PaymentService paymentService;

    /** 模拟支付成功；重复请求返回 409 且不会新增支付记录 */
    @PostMapping("/mock")
    public Result<PaymentVO> mockPay(@Valid @RequestBody MockPaymentDTO dto) {
        return Result.ok(paymentService.mockPay(dto));
    }

    /** 按订单号查询支付结果；必须是当前用户自己的订单 */
    @GetMapping("/orders/{orderNo}")
    public Result<PaymentVO> result(@PathVariable String orderNo) {
        return Result.ok(paymentService.resultByOrderNo(orderNo));
    }

    /** 按订单 ID 查询支付信息（REST 风格便捷接口） */
    @GetMapping("/{orderId}")
    public Result<PaymentVO> resultById(@PathVariable Long orderId) {
        return Result.ok(paymentService.resultByOrderId(orderId));
    }

    /** 按订单 ID 模拟支付（REST 风格便捷接口） */
    @PostMapping("/{orderId}/simulate")
    public Result<PaymentVO> simulateById(@PathVariable Long orderId,
                                          @RequestBody(required = false) MockPaymentDTO dto) {
        String method = dto == null ? null : dto.getPaymentMethod();
        return Result.ok(paymentService.mockPayByOrderId(orderId, method));
    }
}
