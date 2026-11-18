import request from './request'
import type { ApiResponse } from '@/types/api'
import type { AddressFormPayload, AddressVO } from '@/types/user-center'

/** 我的地址列表 */
export function getAddresses() {
  return request.get<ApiResponse<AddressVO[]>>('/v1/addresses')
}

/** 新增地址，返回 id */
export function createAddress(payload: AddressFormPayload) {
  return request.post<ApiResponse<number>>('/v1/addresses', payload)
}

/** 修改地址 */
export function updateAddress(id: number, payload: AddressFormPayload) {
  return request.put<ApiResponse<void>>(`/v1/addresses/${id}`, payload)
}

/** 删除地址 */
export function removeAddress(id: number) {
  return request.delete<ApiResponse<void>>(`/v1/addresses/${id}`)
}

/** 设为默认地址（后端事务中取消旧默认） */
export function setDefaultAddress(id: number) {
  return request.put<ApiResponse<void>>(`/v1/addresses/${id}/default`)
}
