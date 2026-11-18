import request from './request'
import type { ApiResponse, PageResult } from '@/types/api'
import type { AdminDashboardResult, AdminUser, OperationLog, Order, OrderDetail } from '@/types/admin'

/** 平台管理工作台统计（仅管理员） */
export function getAdminDashboard() {
  return request.get<ApiResponse<AdminDashboardResult>>('/v1/admin/dashboard')
}

/** 管理端用户列表 */
export function getAdminUsers(params: { username?: string; pageNum?: number; pageSize?: number }) {
  return request.get<ApiResponse<PageResult<AdminUser>>>('/v1/admin/users', { params })
}

/** 管理端启用/禁用用户；不能禁用自己 */
export function updateAdminUserStatus(id: number, status: number) {
  return request.put<ApiResponse<void>>(`/v1/admin/users/${id}/status`, null, {
    params: { status },
  })
}

/** 管理端平台订单列表 */
export function getAdminOrders(params: {
  orderNo?: string
  status?: string
  pageNum?: number
  pageSize?: number
}) {
  return request.get<ApiResponse<PageResult<Order>>>('/v1/admin/orders', { params })
}

/** 管理端订单详情 */
export function getAdminOrderDetail(id: number) {
  return request.get<ApiResponse<OrderDetail>>(`/v1/admin/orders/${id}`)
}

/** 管理端操作日志 */
export function getOperationLogs(params: { module?: string; pageNum?: number; pageSize?: number }) {
  return request.get<ApiResponse<PageResult<OperationLog>>>('/v1/admin/operation-logs', { params })
}
