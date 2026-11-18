import request from './request'
import type { ApiResponse } from '@/types/api'
import type {
  MerchantDashboardResult,
  MerchantLogoUploadResult,
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

/** 上传店铺头像；字段名固定为 file，后端会校验类型、大小与文件内容 */
export function uploadMerchantLogo(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<ApiResponse<MerchantLogoUploadResult>>('/v1/merchant/profile/logo', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
