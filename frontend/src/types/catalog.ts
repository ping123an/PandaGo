/** 商品分类与商品数据类型，字段与后端 VO 对应 */

export interface CategoryVO {
  id: number
  parentId: number
  name: string
  sort: number
  icon: string | null
  status: number
  children: CategoryVO[]
}

export interface ProductListItem {
  id: number
  categoryId: number
  categoryName: string
  name: string
  subtitle: string | null
  mainImage: string
  price: number
  /** 库存状态：IN_STOCK / OUT_OF_STOCK；用户端不返回具体库存数量 */
  stockStatus: 'IN_STOCK' | 'OUT_OF_STOCK'
  sales: number
  status: string
}

export interface ProductDetail {
  id: number
  categoryId: number
  categoryName: string
  name: string
  subtitle: string | null
  mainImage: string
  price: number
  /** 用户端详情不返回具体库存，仅返回状态；商家/管理端接口另用 ProductManagementItem */
  stock: number | null
  stockStatus: 'IN_STOCK' | 'OUT_OF_STOCK'
  sales: number
  status: string
  auditStatus: string
  auditReason: string | null
  description: string | null
  images: string[]
  createdAt: string
}

/** 商家 / 管理员商品管理列表项 */
export interface ProductManagementItem {
  id: number
  categoryId: number
  categoryName: string
  merchantName: string
  name: string
  mainImage: string
  price: number
  stock: number
  sales: number
  status: 'ON_SALE' | 'OFF_SALE'
  auditStatus: 'PENDING' | 'APPROVED' | 'REJECTED'
  auditReason: string | null
  auditBy: number | null
  auditTime: string | null
  sort: number
  createdAt: string
  updatedAt: string
}

export type ProductSort = 'default' | 'price' | 'sales' | 'newest'
export type ProductOrder = 'asc' | 'desc'

export interface ProductQuery {
  keyword?: string
  categoryId?: number
  minPrice?: number
  maxPrice?: number
  sort?: ProductSort
  order?: ProductOrder
  pageNum: number
  pageSize: number
}

export interface ProductManagementQuery {
  keyword?: string
  categoryId?: number
  status?: 'ON_SALE' | 'OFF_SALE'
  auditStatus?: 'PENDING' | 'APPROVED' | 'REJECTED'
  sort?: ProductSort
  order?: ProductOrder
  pageNum: number
  pageSize: number
}

export interface ProductImageInput {
  url: string
  type: 'MAIN' | 'DETAIL'
  sizeBytes?: number
}

/** 新增/修改商品共用；主图必填，详情图可选 */
export interface ProductFormPayload {
  categoryId: number
  name: string
  subtitle?: string | null
  mainImage: string
  mainImageSizeBytes?: number
  detailImages?: ProductImageInput[]
  price: number
  stock: number
  description?: string | null
  sort: number
}

export interface ProductStatusPayload {
  status: 'ON_SALE' | 'OFF_SALE'
}

export interface ProductAuditPayload {
  auditStatus: 'APPROVED' | 'REJECTED'
  reason?: string | null
}
