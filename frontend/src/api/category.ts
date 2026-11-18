import request from './request'
import type { ApiResponse } from '@/types/api'
import type { CategoryVO } from '@/types/catalog'

/** 用户端：启用的两级分类 */
export function getCategories() {
  return request.get<ApiResponse<CategoryVO[]>>('/v1/categories')
}

/** 管理端：含禁用分类 */
export function getAdminCategories() {
  return request.get<ApiResponse<CategoryVO[]>>('/v1/admin/categories')
}

export interface CategoryFormPayload {
  parentId?: number
  name: string
  sort?: number
  icon?: string | null
}

/** 新增分类（仅 ADMIN） */
export function createCategory(payload: CategoryFormPayload) {
  return request.post<ApiResponse<number>>('/v1/admin/categories', payload)
}

/** 修改分类（仅 ADMIN） */
export function updateCategory(id: number, payload: CategoryFormPayload) {
  return request.put<ApiResponse<void>>(`/v1/admin/categories/${id}`, payload)
}

/** 启用/禁用分类（仅 ADMIN） */
export function updateCategoryStatus(id: number, status: number) {
  return request.put<ApiResponse<void>>(`/v1/admin/categories/${id}/status`, null, {
    params: { status },
  })
}
