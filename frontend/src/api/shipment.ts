import request from './request'
import type { ApiResponse } from '@/types/api'
import type { Shipment, ShipmentPayload } from '@/types/shipment'

/** 用户查看自己订单的物流（只有公司与单号，无轨迹） */
export function getMyShipment(orderId: number) {
  return request.get<ApiResponse<Shipment>>(`/v1/orders/${orderId}/shipment`)
}

/** 商家查看本商家订单物流 */
export function getMerchantShipment(orderId: number) {
  return request.get<ApiResponse<Shipment>>(`/v1/merchant/orders/${orderId}/shipment`)
}

/** 商家发货：只提交物流公司与单号，状态由后端判定 */
export function shipOrder(orderId: number, payload: ShipmentPayload) {
  return request.post<ApiResponse<Shipment>>(`/v1/merchant/orders/${orderId}/ship`, payload)
}
