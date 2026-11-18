import request from './request'
import type { ApiResponse } from '@/types/api'
import type {
  MerchantDashboardResult,
  MerchantProfile,
  MerchantProfileUpdatePayload,
} from '@/types/merchant'

/** 商家工作台统计：只查当前登录商家，URL 不接受 merchantId */
export function getMerchantDashboard() {
  return request.get<ApiResponse<MerchantDashboardResult>>('/v1/merchant/dashboard')
}

/** 查询我的店铺资料 */
export function getMerchantProfile() {
  return request.get<ApiResponse<MerchantProfile>>('/v1/merchant/profile')
}

/** 修改我的店铺资料（白名单字段） */
export function updateMerchantProfile(payload: MerchantProfileUpdatePayload) {
  return request.put<ApiResponse<MerchantProfile>>('/v1/merchant/profile', payload)
}
