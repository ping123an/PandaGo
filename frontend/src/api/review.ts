import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'
import type { Review, ReviewPayload } from '@/types/review'

/** 对已完成订单中的商品评价 */
export function createReview(payload: ReviewPayload) {
  return request.post<ApiResponse<number>>('/v1/reviews', payload)
}

/** 商品评价列表（公开） */
export function getProductReviews(productId: number) {
  return request.get<ApiResponse<Review[]>>(`/v1/reviews/products/${productId}`)
}

/** 我的评价分页 */
export function getMyReviews(pageNum = 1, pageSize = 10) {
  return request.get<ApiResponse<PageResult<Review>>>('/v1/reviews/mine', {
    params: { pageNum, pageSize },
  })
}
