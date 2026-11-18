// 商家工作台与店铺资料类型（V9）。所有统计与资料数据均来自后端，前端不参与计算。

export interface MerchantRecentOrder {
  id: number
  orderNo: string
  totalAmount: number
  status: string
  statusDescription: string
  createdAt: string
  buyerName: string
  buyerPhoneMasked: string
}

export interface MerchantRecentProduct {
  id: number
  name: string
  mainImage: string
  price: number
  stock: number
  status: string
  auditStatus: string
  sales: number
  createdAt: string
}

export interface MerchantDashboardResult {
  storeName: string
  storeStatus: string
  storeStatusDescription: string
  productTotal: number
  productOnSale: number
  pendingShipmentOrders: number
  completedOrders: number
  salesAmount: number
  recentOrders: MerchantRecentOrder[]
  recentProducts: MerchantRecentProduct[]
}

/** 商家店铺资料：status / auditStatus 为只读，前端不得提交 */
export interface MerchantProfile {
  id: number
  userId: number
  storeName: string
  logo: string | null
  description: string | null
  contactName: string | null
  contactPhone: string | null
  address: string | null
  status: string
  statusDescription: string
  auditStatus: string
  auditStatusDescription: string
  createdAt: string
  updatedAt: string
}

/** 资料修改：白名单字段，与后端 MerchantProfileUpdateDTO 对应 */
export interface MerchantProfileUpdatePayload {
  storeName: string
  description?: string | null
  contactName?: string | null
  contactPhone?: string | null
  address?: string | null
  logo?: string | null
}
