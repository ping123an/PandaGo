/** 管理端类型（阶段 8） */
import type { Order, OrderDetail } from '@/types/order'

export interface AdminUser {
  id: number
  username: string
  nickname: string
  /**
   * 统一联系电话。商家账号在个人中心或商家资料修改手机号时会双向同步，
   * 因此平台管理只展示这一个字段，不再区分账号手机号和店铺联系电话。
   */
  phone: string | null
  /** 1 启用 0 禁用 */
  status: number
  roles: string[]
  createdAt: string
}

export interface OperationLog {
  id: number
  operatorId: number
  operatorName: string | null
  module: string
  operation: string
  targetType: string | null
  targetId: number | null
  detail: string | null
  createdAt: string
}

export type { Order, OrderDetail }

/** 平台管理工作台统计（全部来自后端真实查询） */
export interface AdminDashboardResult {
  /** 买家账号数（拥有 USER 角色） */
  userTotal: number
  /** 全部账号数（含商家、管理员） */
  accountTotal: number
  todayNewUsers: number
  productTotal: number
  /** ON_SALE + APPROVED */
  productOnSale: number
  pendingReviewProducts: number
  orderTotal: number
  pendingShipmentOrders: number
  completedOrders: number
  todayNewOrders: number
  /** 模拟支付口径；已支付且未取消订单金额合计 */
  totalSalesAmount: number
  recentOrders: AdminRecentOrder[]
  pendingReviewProductList: AdminPendingProduct[]
}

export interface AdminRecentOrder {
  id: number
  orderNo: string
  totalAmount: number
  status: string
  statusDescription: string
  createdAt: string
  buyerName: string
  buyerPhoneMasked: string
  firstProductName: string
  itemCount: number
}

export interface AdminPendingProduct {
  id: number
  name: string
  mainImage: string
  price: number
  merchantName: string
  createdAt: string
  auditStatus: string
}
