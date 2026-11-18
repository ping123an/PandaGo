/** 后端统一响应结构，与 com.pandago.common.api.Result 对应 */
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

/** 后端统一分页结构，与 com.pandago.common.api.PageResult 对应 */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
  pages: number
}
