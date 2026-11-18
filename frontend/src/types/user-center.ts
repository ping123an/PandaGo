/** 阶段 5：购物车 / 收藏 / 收货地址类型，字段与后端 VO 对应 */

export interface CartItemVO {
  id: number
  productId: number
  productName: string
  mainImage: string
  /** 商家 ID，来自商品真实归属，供前端按商家分组 */
  merchantId: number | null
  /** 商家展示名称 */
  merchantName: string
  price: number | null
  quantity: number
  stock: number
  selected: boolean
  available: boolean
  invalidReason: string
  subtotal: number
}

export interface CartVO {
  items: CartItemVO[]
  totalQuantity: number
  estimatedTotal: number
  invalidCount: number
  allSelected: boolean
}

export interface CartAddPayload {
  productId: number
  quantity: number
}

export interface CartUpdatePayload {
  quantity?: number
  selected?: boolean
}

export interface FavoriteVO {
  id: number
  productId: number
  productName: string
  mainImage: string
  price: number | null
  status: string | null
  auditStatus: string | null
  available: boolean
  reason: string
  createdAt: string
}

export interface AddressVO {
  id: number
  receiverName: string
  phone: string
  province: string
  city: string
  district: string
  detailAddress: string
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

/** 新增/修改地址共用；isDefault 可选 */
export interface AddressFormPayload {
  receiverName: string
  phone: string
  province: string
  city: string
  district: string
  detailAddress: string
  isDefault?: boolean
}
