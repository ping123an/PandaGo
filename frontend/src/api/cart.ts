import request from './request'
import type { ApiResponse } from '@/types/api'
import type { CartAddPayload, CartUpdatePayload, CartVO } from '@/types/user-center'

/** 查询我的购物车（价格实时来自商品表） */
export function getCart() {
  return request.get<ApiResponse<CartVO>>('/v1/cart')
}

/** 加入购物车，返回条目 id */
export function addCartItem(payload: CartAddPayload) {
  return request.post<ApiResponse<number>>('/v1/cart/items', payload)
}

/** 修改数量或勾选状态 */
export function updateCartItem(id: number, payload: CartUpdatePayload) {
  return request.put<ApiResponse<void>>(`/v1/cart/items/${id}`, payload)
}

/** 删除购物车条目 */
export function removeCartItem(id: number) {
  return request.delete<ApiResponse<void>>(`/v1/cart/items/${id}`)
}
