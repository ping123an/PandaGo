// 支付相关类型（阶段 7）。金额与状态均由服务端返回，前端不参与计算。

export interface PaymentResult {
  id: number | null
  paymentNo: string | null
  orderId: number
  orderNo: string
  payAmount: number
  paymentMethod: string
  status: string
  paidAt: string | null
  paid: boolean
  orderStatus: string
  orderStatusDescription: string
  createdAt: string | null
}
