/** 物流信息（阶段 8）：本项目只有物流公司与单号，不含轨迹数据 */
export interface Shipment {
  id: number
  orderId: number
  orderNo: string
  company: string
  trackingNo: string
  /** 发货时间：仅发货成功才有 */
  shippedAt?: string
}

/** 商家发货表单：只提交物流公司与单号 */
export interface ShipmentPayload {
  company: string
  trackingNo: string
}
