import request from './request'
import type { ApiResponse } from '@/types/api'
import type { FavoriteVO } from '@/types/user-center'

/** 添加收藏 */
export function addFavorite(productId: number) {
  return request.post<ApiResponse<number>>('/v1/favorites', { productId })
}

/** 取消收藏 */
export function removeFavorite(productId: number) {
  return request.delete<ApiResponse<void>>(`/v1/favorites/${productId}`)
}

/** 我的收藏列表 */
export function getFavorites() {
  return request.get<ApiResponse<FavoriteVO[]>>('/v1/favorites')
}

/** 查询指定商品是否已收藏 */
export function checkFavorite(productId: number) {
  return request.get<ApiResponse<boolean>>('/v1/favorites/check', { params: { productId } })
}
