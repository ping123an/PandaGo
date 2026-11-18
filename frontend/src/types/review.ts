/** 商品评价（阶段 8） */
export interface Review {
  id: number
  userId: number
  username: string
  productId: number
  orderItemId: number
  rating: number
  content: string
  createdAt: string
}

/** 评价表单：只提交订单明细ID、评分、内容，归属由后端校验 */
export interface ReviewPayload {
  orderItemId: number
  rating: number
  content: string
}
