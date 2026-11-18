package com.pandago.payment.service;

import com.pandago.payment.dto.MockPaymentDTO;
import com.pandago.payment.vo.PaymentVO;

/** 模拟支付入口：只负责校验与编排，事务写在独立的 PaymentTransactionService 中。 */
public interface PaymentService {

    /** 模拟支付；重复支付返回明确业务冲突，不重复生成支付记录 */
    PaymentVO mockPay(MockPaymentDTO dto);

    /** 按订单号查询支付结果；必须校验当前用户归属 */
    PaymentVO resultByOrderNo(String orderNo);

    /** 按订单 ID 查询支付结果（REST 风格便捷接口）；必须校验当前用户归属 */
    PaymentVO resultByOrderId(Long orderId);

    /** 按订单 ID 模拟支付（REST 风格便捷接口）；必须校验当前用户归属 */
    PaymentVO mockPayByOrderId(Long orderId, String paymentMethod);
}
