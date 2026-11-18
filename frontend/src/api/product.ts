import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'
import type {
  ProductAuditPayload,
  ProductDetail,
  ProductFormPayload,
  ProductListItem,
  ProductManagementItem,
  ProductManagementQuery,
  ProductQuery,
  ProductStatusPayload,
} from '@/types/catalog'

/** 用户端：商品分页列表 */
export function getProducts(params: ProductQuery) {
  return request.get<ApiResponse<PageResult<ProductListItem>>>('/v1/products', { params })
}

/** 用户端：热门商品（只返回审核通过且上架） */
export function getHotProducts(limit = 4) {
  return request.get<ApiResponse<ProductListItem[]>>('/v1/products/hot', { params: { limit } })
}

/** 用户端：商品详情（仅审核通过且上架） */
export function getProductDetail(id: number) {
  return request.get<ApiResponse<ProductDetail>>(`/v1/products/${id}`)
}

/** 商家端：我的商品分页列表 */
export function getMyProducts(params: ProductManagementQuery) {
  return request.get<ApiResponse<PageResult<ProductManagementItem>>>('/v1/merchant/products', { params })
}

/** 商家端：我的商品详情 */
export function getMyProductDetail(id: number) {
  return request.get<ApiResponse<ProductDetail>>(`/v1/merchant/products/${id}`)
}

/** 商家端：新增商品 */
export function createMyProduct(payload: ProductFormPayload) {
  return request.post<ApiResponse<number>>('/v1/merchant/products', payload)
}

/** 商家端：修改自己的商品 */
export function updateMyProduct(id: number, payload: ProductFormPayload) {
  return request.put<ApiResponse<void>>(`/v1/merchant/products/${id}`, payload)
}

/** 商家端：上架/下架自己的商品 */
export function updateMyProductStatus(id: number, payload: ProductStatusPayload) {
  return request.put<ApiResponse<void>>(`/v1/merchant/products/${id}/status`, payload)
}

/** 商家端：提交审核 */
export function submitProductAudit(id: number) {
  return request.post<ApiResponse<void>>(`/v1/merchant/products/${id}/submit-audit`)
}

/** 管理端：全平台商品分页（主要用于待审核列表） */
export function getAdminProducts(params: ProductManagementQuery) {
  return request.get<ApiResponse<PageResult<ProductManagementItem>>>('/v1/admin/products', { params })
}

/** 管理端：商品详情（含主图/详情图，可查看未上架/待审核商品） */
export function getAdminProductDetail(id: number) {
  return request.get<ApiResponse<ProductDetail>>(`/v1/admin/products/${id}`)
}

/** 管理端：审核商品 */
export function auditProduct(id: number, payload: ProductAuditPayload) {
  return request.put<ApiResponse<void>>(`/v1/admin/products/${id}/audit`, payload)
}

/** 管理端：平台上架/下架商品 */
export function updateAdminProductStatus(id: number, payload: ProductStatusPayload) {
  return request.put<ApiResponse<void>>(`/v1/admin/products/${id}/status`, payload)
}
