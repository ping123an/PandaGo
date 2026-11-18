import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'
import type {
  Order,
  OrderConfirmResult,
  OrderCreateResult,
  OrderDetail,
  OrderPreviewResult,
  OrderQuery,
  OrderStatusResult,
} from '@/types/order'

/** 创建订单请求：只提交必要信息，绝不包含价格 / userId / merchantId */
export interface OrderCreatePayload {
  addressId: number
  cartItemIds?: number[]
  items?: Array<{ productId: number; quantity: number }>
  remark?: string
  /** 结算幂等键：同一次提交重试复用同一值，防止重复建单 */
  checkoutGroupId?: string
}

export function createOrder(payload: OrderCreatePayload) {
  return request.post<ApiResponse<OrderCreateResult>>('/v1/orders', payload)
}

/** 订单预览：只查库计算，不写库；价格/商家/库存由后端重新校验 */
export interface OrderPreviewPayload {
  source: 'CART' | 'DIRECT'
  addressId: number
  items: Array<{ productId: number; quantity: number }>
}

export function previewOrder(payload: OrderPreviewPayload) {
  return request.post<ApiResponse<OrderPreviewResult>>('/v1/orders/preview', payload)
}

export function getOrders(query: OrderQuery) {
  return request.get<ApiResponse<PageResult<Order>>>('/v1/orders', { params: query })
}

export function getOrderDetail(id: number) {
  return request.get<ApiResponse<OrderDetail>>(`/v1/orders/${id}`)
}

/** 轻量状态查询：由后端状态机决定当前可执行操作 */
export function getOrderStatus(id: number) {
  return request.get<ApiResponse<OrderStatusResult>>(`/v1/orders/${id}/status`)
}

/** 用户取消待支付订单；已支付/已取消由后端拒绝 */
export function cancelOrder(id: number) {
  return request.post<ApiResponse<{ id: number; orderNo: string; status: string; statusDescription: string }>>(
    `/v1/orders/${id}/cancel`,
  )
}

/** 用户确认收货；仅本人待收货订单 */
export function confirmOrder(id: number) {
  return request.post<ApiResponse<OrderConfirmResult>>(`/v1/orders/${id}/confirm`)
}
