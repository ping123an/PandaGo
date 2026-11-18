// 订单相关类型（阶段 6）。所有金额来自服务端；前端仅作预览展示。

export interface OrderItem {
  id: number
  productId: number
  productName: string
  productImage: string
  unitPrice: number
  quantity: number
  subtotal: number
}

export interface Order {
  id: number
  orderNo: string
  totalAmount: number
  status: string
  statusDescription: string
  remark: string
  createdAt: string
  firstProductName: string
  firstProductImage: string
  itemCount: number
  items: OrderItem[]
}

export interface OrderDetail extends Order {
  receiverName: string
  receiverPhone: string
  receiverProvince: string
  receiverCity: string
  receiverDistrict: string
  receiverDetailAddress: string
}

/** 拆单后单个子订单摘要 */
export interface OrderSummary {
  id: number
  orderNo: string
  merchantId: number | null
  totalAmount: number
  status: string
  statusDescription: string
}

export interface OrderCreateResult {
  id: number
  orderNo: string
  totalAmount: number
  status: string
  statusDescription: string
  /** 结算批次号，一次提交拆出的订单共享 */
  checkoutGroupId: string
  /** 本次提交生成的订单数量（多商家拆单 > 1） */
  orderCount: number
  /** 本次提交所有订单金额合计 */
  grandTotal: number
  /** 各子订单摘要 */
  orders: OrderSummary[]
}

export interface OrderQuery {
  status?: string
  pageNum?: number
  pageSize?: number
}

/** 轻量状态查询结果：allowedOperations 由后端状态机给出，前端不自行推导规则 */
export interface OrderStatusResult {
  id: number
  orderNo: string
  status: string
  statusDescription: string
  allowedOperations: string[]
  canPay: boolean
  canCancel: boolean
  canShip: boolean
  canConfirm: boolean
  canReview: boolean
}

/** 订单预览（立即购买 / 购物车结算共用），全部由后端基于数据库计算 */
export interface OrderPreviewItem {
  productId: number
  productName: string
  productImage: string
  specText: string
  unitPrice: number
  quantity: number
  subtotal: number
  merchantId: number | null
  merchantName: string
}

export interface OrderPreviewMerchantGroup {
  merchantId: number | null
  merchantName: string
  items: OrderPreviewItem[]
  subtotal: number
}

export interface OrderPreviewAddress {
  id: number
  receiverName: string
  phone: string
  province: string
  city: string
  district: string
  detailAddress: string
  isDefault: boolean
}

export interface OrderPreviewResult {
  source: 'CART' | 'DIRECT'
  address: OrderPreviewAddress[]
  items: OrderPreviewItem[]
  merchantGroups: OrderPreviewMerchantGroup[]
  totalAmount: number
  discountAmount: number
  freightAmount: number
  payableAmount: number
  merchantCount: number
}

/** 确认收货结果 */
export interface OrderConfirmResult {
  id: number
  orderNo: string
  status: string
  statusDescription: string
  receivedAt: string
}
