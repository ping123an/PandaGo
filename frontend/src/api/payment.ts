import request from './request'
import type { ApiResponse } from '@/types/api'
import type { PaymentResult } from '@/types/payment'

/** 只提交订单号；金额永远由后端从订单快照读取 */
export interface MockPaymentPayload {
  orderNo: string
  /** 支付方式标记：ALIPAY / WECHAT / MOCK（缺省 MOCK） */
  paymentMethod?: string
}

export function mockPay(payload: MockPaymentPayload) {
  return request.post<ApiResponse<PaymentResult>>('/v1/payments/mock', payload)
}

export function getPaymentResult(orderNo: string) {
  return request.get<ApiResponse<PaymentResult>>(`/v1/payments/orders/${encodeURIComponent(orderNo)}`)
}
