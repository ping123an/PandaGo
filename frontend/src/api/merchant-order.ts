import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'
import type { Order, OrderDetail } from '@/types/order'

/** 商家订单列表：可按状态筛选 */
export function getMerchantOrders(params: { status?: string; pageNum?: number; pageSize?: number }) {
  return request.get<ApiResponse<PageResult<Order>>>('/v1/merchant/orders', { params })
}

/** 商家订单详情 */
export function getMerchantOrderDetail(id: number) {
  return request.get<ApiResponse<OrderDetail>>(`/v1/merchant/orders/${id}`)
}
