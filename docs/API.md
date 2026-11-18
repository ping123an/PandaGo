# PandaGo API 文档

> 基础路径：`/api/v1`。统一响应为 `{ code, message, data, timestamp }`，分页统一为 `{ list, total, pageNum, pageSize, pages }`。
> 阶段 1–3 的健康检查与认证接口见 `README.md` 与 `docs/AUTH.md`；本文档重点记录阶段 4 商品相关接口。

## 1. 权限与错误约定

| 错误码 | HTTP | 含义 |
| --- | --- | --- |
| 400 | 400 | 参数错误、排序字段非法、价格区间反置、图片 URL 不合法 |
| 401 | 401 | 未登录或 Token 无效/过期 |
| 403 | 403 | 角色不足，或商家尝试操作他人商品 |
| 404 | 404 | 商品/订单/地址不存在、非本人资源（统一 404 防探测），或未映射的路径 |
| 409 | 409 | 业务冲突，例如未审核通过就上架、已通过仍提交审核、重复支付/取消/评价 |
| 500 | 500 | 未预期系统异常；堆栈只写服务端日志，返回通用提示不泄露内部细节 |

> 统一异常处理由 `GlobalExceptionHandler` 实现：业务异常码按上表映射到 HTTP 状态；参数校验
> （`@Valid` / `@Min` / `@Pattern` 等）返回 400 并拼接字段名提示；`AccessDeniedException`
> 返回 403；Spring Boot 3.2 的 `NoResourceFoundException`（未知路径）返回 404。
>
> 商家端接口必须携带 `Authorization: Bearer <JWT>`，且 Token 角色必须包含 `MERCHANT`。管理员审核接口需要 `ADMIN`。

## 2. 用户端浏览接口（游客可用）

### 2.1 `GET /categories`

返回启用的两级分类树：`id / parentId / name / sort / icon / status / children`。

### 2.2 `GET /products`

商品条件分页。只返回 `status=ON_SALE`、`audit_status=APPROVED`、`deleted=0` 的商品。

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | string | 商品名/副标题关键词，最长 50，只允许中文、字母、数字、空格、连字符 |
| `categoryId` | long | 分类 ID，可传一级或二级分类 |
| `minPrice` | decimal | 最低价，含边界，不能为负 |
| `maxPrice` | decimal | 最高价，含边界，不能为负；不得小于 `minPrice` |
| `sort` | string | `default` / `price` / `sales` / `newest`，白名单外返回 400 |
| `order` | string | `asc` / `desc`，其他值返回 400 |
| `pageNum` | long | 从 1 开始 |
| `pageSize` | long | 1–50 |

响应示例：

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "list": [{ "id": 1, "name": "熊猫蓝牙耳机", "price": 199.00, "sales": 320 }],
    "total": 10,
    "pageNum": 1,
    "pageSize": 12,
    "pages": 1
  }
}
```

### 2.3 `GET /products/hot?limit=4`

首页热门推荐。可见性规则与 `/products` 相同，按销量、创建时间倒序。`limit` 范围 1–20，默认 4。

### 2.4 `GET /products/{id}`

商品详情。包含主图、详情图数组、价格、库存、销量、分类名、描述。不可见商品返回 404。

## 3. 商家端商品管理（需要 `MERCHANT`）

> 所有商家接口的商品归属由服务端从 SecurityContext 读取 `userId` 并强制作为查询条件；请求体中的 `merchantId` / `userId` / `role` 均不接收。

### 3.1 `GET /merchant/products`

我的商品分页。支持 `keyword / status / auditStatus / sort / order / pageNum / pageSize`，返回 `ProductManagementVO`（含 `auditReason`）。

### 3.2 `GET /merchant/products/{id}`

我的商品详情。他人商品返回 403，不存在的商品返回 404。

### 3.3 `POST /merchant/products`

新增商品。请求体：

```json
{
  "categoryId": 1,
  "name": "熊猫新款耳机",
  "subtitle": "轻量佩戴",
  "mainImage": "/images/panda-product.svg",
  "mainImageSizeBytes": 204800,
  "detailImages": [
    { "url": "/images/panda-product-detail.svg", "type": "DETAIL", "sizeBytes": 102400 }
  ],
  "price": 199.00,
  "stock": 20,
  "description": "商品详情文字",
  "sort": 1
}
```

规则：
- 后端固定 `merchantId`、审核状态 `PENDING`、上架状态 `OFF_SALE`、`sales=0`；
- 图片 URL 仅允许配置白名单前缀（默认 `/images/`、`http://`、`https://`），扩展名允许 `jpg/jpeg/png/webp/gif/svg`，URL 长度不超过 255，声明大小默认不超过 5 MB；
- `sizeBytes` 只是请求声明值，**不是服务端读取文件流后的真实大小校验**；本项目尚未接入对象存储/上传接口；
- 图片与商品主表在同一事务内写入，失败一起回滚。

返回新增商品 ID。

### 3.4 `PUT /merchant/products/{id}`

修改自己的商品。字段与新增基本相同；`mainImage` 不传表示保留原主图；`detailImages` 传数组表示整体替换详情图。状态、审核状态、商家 ID 不能在修改接口改变。

### 3.5 `PUT /merchant/products/{id}/status`

上架/下架：`{ "status": "ON_SALE" | "OFF_SALE" }`。未审核通过不能上架。

### 3.6 `POST /merchant/products/{id}/submit-audit`

提交审核。`PENDING` / `REJECTED` 可提交；`APPROVED` 返回 409。提交后强制 `OFF_SALE`。

## 4. 管理端商品审核（需要 `ADMIN`）

### 4.1 `GET /admin/products`

全平台商品分页，支持 `keyword / status / auditStatus / categoryId / sort / order / pageNum / pageSize`。
四个筛选条件彼此独立：`status` 表示上架状态（`ON_SALE/OFF_SALE`），`auditStatus` 表示审核状态
（`PENDING/APPROVED/REJECTED`），可组合使用。清单额外返回 `merchantName / auditBy / auditTime`。
非法状态参数返回 400。

### 4.2 `GET /admin/products/{id}`

可查看待审核、驳回、下架商品详情，包含主图、详情图、`stock`、`auditStatus / auditReason`。

### 4.3 `PUT /admin/products/{id}/audit`

```json
{ "auditStatus": "APPROVED" }
```
或
```json
{ "auditStatus": "REJECTED", "reason": "图片不清晰" }
```

规则：仅 `ADMIN` 可调用；重复审核（目标状态等于当前状态）返回 409；已驳回商品不能直接改为已通过，
需商家重新提交审核；`REJECTED` 必须填写原因（缺失返回 400），并强制下架。
审核通过/驳回都会记录 `audit_by / audit_time`，通过后不会自动上架。

### 4.4 `PUT /admin/products/{id}/status`

上架/下架。上架前后端再次校验审核状态：`PENDING` 返回「商品待审核，不能上架」，
`REJECTED` 返回「商品审核已驳回，不能上架」，均为 409；已通过商品可上架、下架和重新上架。

### 4.5 阶段 2 保留的基础维护

`POST /admin/products`、`PUT /admin/products/{id}`、`PUT /admin/products/{id}/status` 仍可用，操作的是 `merchantId=0` 的平台演示商品，不替代商家端流程。

---

## 5. 购物车（阶段 5，全部需要 `USER` 登录）

> 所有购物车接口都不接收 `userId`；当前用户由 JWT 验签后的 SecurityContext 决定。
> 展示价格实时从 `product` 读取，购物车表不保存价格，也不是成交价。

### 5.1 `GET /cart`

返回我的购物车：

```json
{
  "code": 200,
  "data": {
    "items": [{
      "id": 12,
      "productId": 4,
      "productName": "熊猫蓝牙耳机",
      "mainImage": "/images/panda-product.svg",
      "price": 199.00,
      "quantity": 2,
      "stock": 20,
      "selected": true,
      "available": true,
      "invalidReason": "",
      "subtotal": 398.00
    }],
    "totalQuantity": 2,
    "estimatedTotal": 398.00,
    "invalidCount": 0,
    "allSelected": true
  }
}
```

`available=false` 表示商品下架 / 未审核 / 售罄 / 库存不足 / 已删除，`invalidReason` 给出原因，
该类商品不计入 `estimatedTotal`，需用户重新确认。`estimatedTotal` 仅统计已勾选且可购买条目。

### 5.2 `POST /cart/items`

```json
{ "productId": 4, "quantity": 2 }
```

`quantity` 1–999。校验商品存在、上架、审核通过且库存充足。同一用户同一商品只保留一行，
重复添加合并数量。成功返回条目 id；参数错误 400，商品不可售 / 合并后超库存 409。

### 5.3 `PUT /cart/items/{id}`

```json
{ "quantity": 3, "selected": false }
```

两个字段均可选，只更新传入字段。数量必须 ≥1 且 ≤ 当前库存。他人条目 / 不存在条目返回 404。

### 5.4 `DELETE /cart/items/{id}`

删除我的条目；他人条目返回 404。

## 6. 商品收藏（阶段 5，全部需要 `USER` 登录）

### 6.1 `POST /favorites`

请求体 `{"productId": 4}`。要求商品当前已上架且审核通过；重复收藏返回 409
（由 `uk_favorite_user_product(user_id, product_id)` 唯一索引兜底）。返回收藏 id。

### 6.2 `DELETE /favorites/{productId}`

按商品 id 取消我的收藏；不存在返回 404。他人收藏无法被删除。

### 6.3 `GET /favorites`

我的收藏列表。商品后续下架 / 删除时收藏关系仍保留，通过 `available` / `reason` 展示状态。

### 6.4 `GET /favorites/check?productId=4`

返回 `true` / `false`，供商品卡片与详情页收藏按钮回显状态。

## 7. 收货地址（阶段 5，全部需要 `USER` 登录）

地址字段：`receiverName`(≤50)、`phone`(11 位手机号)、`province` / `city` / `district`(≤50)、
`detailAddress`(≤200)、`isDefault`(可选)。所有写操作只作用于当前用户自己的地址。

### 7.1 `GET /addresses` — 我的地址列表，默认地址排在最前

### 7.2 `POST /addresses` — 新增地址，返回 id；`isDefault=true` 时自动清掉旧默认

### 7.3 `PUT /addresses/{id}` — 全量修改地址字段；`isDefault=false` 表示取消默认

### 7.4 `DELETE /addresses/{id}` — 删除地址

删除默认地址时先把 `is_default` 置 0 再逻辑删除；**PRD 未规定自动补位，本实现不自动指定新默认**，
用户需显式设置。理由：自动切换会静默改变后续下单的默认收货地址。

### 7.5 `PUT /addresses/{id}/default` — 设为默认

在同一事务中先取消我的所有默认地址，再把目标设为默认；数据库层用生成列
`default_flag` + 唯一索引 `uk_address_user_default` 保证同一用户最多一个默认地址。

---

## 8. 订单（阶段 6，全部需要 `USER` 登录）

> 请求只接收必要信息：购物车条目 ID 或商品 ID+数量（二选一）、地址 ID、备注。
> **不接收** price / totalAmount / payAmount / userId / merchantId；即使请求体夹带这些字段也会被忽略。
> 金额由后端按数据库实时价格计算；身份来自 SecurityContext；商品与地址信息在下单时刻固化为快照。

### 8.1 `POST /orders` — 创建订单（事务性）

购物车结算模式（推荐，结算后自动清理这些条目）：

```json
{ "addressId": 19, "cartItemIds": [12, 13], "remark": "请尽快送达" }
```

直接购买模式（不改购物车）：

```json
{ "addressId": 19, "items": [{ "productId": 4, "quantity": 2 }] }
```

成功返回：

```json
{ "code": 200, "data": {
  "id": 1, "orderNo": "PO20261117155826E3966B6F",
  "totalAmount": 598.00, "status": "PENDING_PAYMENT", "statusDescription": "待支付"
} }
```

事务内顺序：条件扣库存（受影响行数=0 即失败）→ 订单主表 → 订单明细 → 清理购物车，
任一步失败整体回滚。校验失败码：地址/商品不存在 404；下架、未审核、库存不足、跨商家 409；
参数错误 400。

### 8.2 `GET /orders` — 我的订单分页

参数：`pageNum`(默认1)、`pageSize`(默认10)、`status`(可选：PENDING_PAYMENT / PENDING_SHIPMENT /
PENDING_RECEIPT / COMPLETED / CANCELLED)。列表项含订单号、金额、状态中文描述、首件商品快照、
明细条数；只返回当前用户订单。

### 8.3 `GET /orders/{id}` — 订单详情

含订单主表快照、收货地址快照（`receiverName` / `receiverPhone` / 省市区 / 详细地址）和
商品明细快照（`productName` / `productImage` / `unitPrice` / `quantity` / `subtotal`）。
全部来自下单时刻，不依赖商品当前名称、图片或价格。他人订单与不存在订单统一返回 404。

### 8.4 `GET /orders/{id}/status` — 订单状态查询（阶段 7）

轻量查询，返回 `id / orderNo / status / statusDescription / allowedOperations / canPay / canCancel`。
`allowedOperations` 由后端状态机计算，不由前端推导。只返回当前用户订单，否则 404。

### 8.5 `POST /orders/{id}/cancel` — 用户取消待支付订单（阶段 7）

无请求体。只有 `PENDING_PAYMENT` 订单允许取消：状态条件更新成功后，才按 `order_item` 快照回补库存，
两步在同一事务内。重复取消 / 并发取消 / 已支付订单取消均返回 409，且不会重复回补库存。
他人订单返回 404。

---

## 9. 模拟支付（阶段 7，全部需要 `USER` 登录）

> 本项目不接真实支付平台。支付金额只从 `orders.total_amount` 读取；请求体即使夹带 `amount` /
> `payAmount` / `totalAmount` 也会被忽略。所有查询都按当前登录用户校验归属。

### 9.1 `POST /payments/mock` — 模拟支付成功

```json
{ "orderNo": "PO20261117155826E3966B6F" }
```

成功响应（节选）：

```json
{ "code": 200, "data": {
  "id": 1, "paymentNo": "PAY20261117155826ABCDEFGHIJKL",
  "orderNo": "PO20261117155826E3966B6F",
  "payAmount": 598.00, "paymentMethod": "MOCK", "status": "SUCCESS",
  "paid": true, "paidAt": "2026-11-17 16:00:00",
  "orderStatus": "PENDING_SHIPMENT", "orderStatusDescription": "待发货"
} }
```

规则：
- 只处理自己的 `PENDING_PAYMENT` 订单；他人订单 / 不存在订单返回 404；
- 已取消 / 已支付 / 其他非法状态返回 409；重复支付提示中带原支付流水号；
- `payment_record` 与订单状态更新在同一事务；`payment_no`、`order_id` 均为唯一索引；
- 并发支付只有一个成功，另一个返回 409，不重复生成支付记录。

### 9.2 `GET /payments/orders/{orderNo}` — 支付结果查询

返回与 9.1 相同结构。未支付时 `paymentNo` / `paidAt` 为 null、`paid=false`，
`payAmount` 显示订单应付金额；已支付时显示实际支付金额与流水号。他人订单返回 404。

---

## 10. 发货、确认收货与物流（阶段 8）

### 商家端（需 `MERCHANT` 角色，归属本商家）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/merchant/orders?status=&pageNum=&pageSize=` | 我的商家订单分页，`status` 仅作筛选 |
| GET | `/merchant/orders/{id}` | 本商家订单详情（含地址与商品快照） |
| POST | `/merchant/orders/{id}/ship` | 发货，请求体 `{"company":"...","trackingNo":"..."}` |
| GET | `/merchant/orders/{id}/shipment` | 本商家订单物流信息 |

`POST ship` 规则：只有 `PENDING_SHIPMENT` 且归属当前商家的订单可发货；物流公司非空（≤50），
单号 4~64 字符。成功后订单变 `PENDING_RECEIPT`，写 `shipment`（公司/单号/发货时间）与操作日志，
同一事务完成。重复/越权/非待发货返回 403 或 409，不产生第二条物流。
响应 `ShipmentVO` 只有公司与单号，**不含任何轨迹字段**。

### 用户端（需登录，归属本人）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/orders/{id}/confirm` | 确认收货，仅本人 `PENDING_RECEIPT` 订单 |
| GET | `/orders/{id}/shipment` | 我的订单物流；未发货返回 409 |

`POST confirm` 成功返回 `{ id, orderNo, status: "COMPLETED", statusDescription, receivedAt }`。
重复确认返回 409，无副作用；他人订单 404；待发货订单确认返回 409。
`/orders/{id}/status` 的 `allowedOperations` / `canShip` / `canConfirm` / `canReview` 由后端状态机给出，
前端据此显示按钮，不自行推导规则。

---

## 11. 商品评价（阶段 8）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/reviews` | 登录用户 | 评价已完成订单中的商品 |
| GET | `/reviews/products/{productId}` | 公开 | 商品评价列表（游客可看） |
| GET | `/reviews/mine?pageNum=&pageSize=` | 登录用户 | 我的评价分页 |

`POST /reviews` 请求体：

```json
{ "orderItemId": 12, "rating": 5, "content": "轻便舒适，通勤戴着很合适" }
```

规则：只传订单明细 ID、评分和内容；用户归属、订单是否完成、商品归属由后端按 `order_item_id` 反查校验。
评分 1~5，内容 5~500 字符。同一订单商品只能评价一次：`uk_review_order_item(order_item_id)` 唯一索引 +
先查后插友好提示，重复返回 409。未完成订单评价返回 409，他人订单明细返回 404。
评价列表只返回评价所需字段（用户昵称、评分、内容、时间），不暴露订单/地址快照。

---

## 12. 管理端（阶段 8，全部需 `ADMIN` 角色）

URL 规则 + 方法级 `@PreAuthorize` + Service `requireRole` 三重保护。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/admin/dashboard` | 平台工作台统计（真实聚合数据，见下方口径） |
| GET | `/admin/users?username=&pageNum=&pageSize=` | 用户列表（含角色，不含密码哈希） |
| PUT | `/admin/users/{id}/status?status=0\|1` | 启用/禁用用户；不能禁用自己 |
| GET | `/admin/categories` | 含禁用分类的两级分类树 |
| POST | `/admin/categories` | 新增分类 `{parentId,name,sort,icon}` |
| PUT | `/admin/categories/{id}` | 修改分类 |
| PUT | `/admin/categories/{id}/status?status=0\|1` | 启用/禁用分类 |
| PUT | `/admin/products/{id}/audit` | 商品审核 `{auditStatus,reason}`（阶段 4 已有，阶段 8 补审核留痕） |
| GET | `/admin/products` | 平台商品分页查询 |
| PUT | `/admin/products/{id}/status` | 平台上架/下架商品 |
| GET | `/admin/orders?orderNo=&status=&pageNum=&pageSize=` | 平台订单分页查询（只读） |
| GET | `/admin/orders/{id}` | 平台订单详情（只读） |
| GET | `/admin/operation-logs?module=&pageNum=&pageSize=` | 操作日志分页 |

审核响应为 `Result.ok()`，但会把审核人（`audit_by`）、审核时间（`audit_time`）与审核意见
（`audit_reason`）写入 `product`，并生成一条 `operation_log`（`module=PRODUCT`, `operation=AUDIT`）。
管理员不开放修改订单状态的接口，也不开放修改用户角色的接口；平台订单为只读。

### 12.1 `GET /admin/dashboard` 统计口径

- 无入参；不接受前端传入的管理员 ID。URL、方法注解和 Service 三处都要求 `ADMIN`。
- `userTotal` 为拥有 `USER` 角色的买家账号数，`accountTotal` 为全部未删除账号数；
  两者使用 `COUNT(DISTINCT user.id)`，多角色账号不会重复计数。
- `productTotal` 为未删除商品数；`productOnSale` 只计 `ON_SALE + APPROVED`；
  `pendingReviewProducts` 只计 `PENDING`。
- `orderTotal / pendingShipmentOrders / completedOrders / todayNewOrders` 来自 `orders` 聚合。
- `totalSalesAmount` 是模拟支付金额：只统计存在 `payment_record.status='SUCCESS'` 且订单状态为
  `PENDING_SHIPMENT / PENDING_RECEIPT / COMPLETED` 的 `orders.total_amount` 快照；
  未支付与已取消订单不计入。项目无退款字段，因此不存在退款订单。
- `recentOrders` 按创建时间倒序取 5 条，买家手机号只返回脱敏值；
  `pendingReviewProductList` 为待审核商品按提交时间倒序取 5 条。
- 实现共 5 条 SQL（3 条聚合 + 2 条 LIMIT 明细），没有逐条回查，避免 N+1。

---

## 13. Redis 缓存与超时关闭（阶段 9，对现有接口透明）

阶段 9 **不新增对外 HTTP 接口**：缓存与定时任务是横切增强，现有接口行为不变。

### 13.1 缓存透明性

以下读接口在启用缓存（`PANDAGO_CACHE_ENABLED=true`）后走 Cache Aside，未启用或 Redis 不可用时自动降级直查 MySQL，**响应结构完全不变**：

| 接口 | 缓存 Key | TTL |
| --- | --- | --- |
| `GET /products/{id}` | `pandago:product:detail:{id}` | 10 分钟 |
| `GET /categories` | `pandago:category:list` | 10 分钟 |
| `GET /products/hot?limit=` | `pandago:product:hot:{limit}` | 5 分钟 |
| 不存在的商品详情 | 同上，写 `__PANDAGO_NULL__` 空值标记 | 30 秒（防穿透） |

写操作（商品新增/修改/上下架/审核、分类增删改）成功后会删除对应缓存，下次读回源拿最新值。

### 13.2 超时关闭（后台定时任务，无对外接口）

`OrderTimeoutScheduler` 按配置周期扫描，将「创建超过 `pandago.order.timeout.minutes`（默认 30）分钟仍未支付」
的订单从 `PENDING_PAYMENT` 条件更新为 `CANCELLED`，并在同一事务回补库存。
只有影响 1 行的订单才回补，重复扫描不会重复回补。管理员可在 `GET /admin/orders` 查看被关闭的订单（状态 CANCELLED）。

相关配置（环境变量）：`PANDAGO_CACHE_ENABLED`、`PANDAGO_CACHE_KEY_PREFIX`、`PANDAGO_REDIS_HOST`、
`PANDAGO_REDIS_PORT`、`PANDAGO_REDIS_PASSWORD`、`PANDAGO_ORDER_TIMEOUT_MINUTES`、
`PANDAGO_ORDER_TIMEOUT_BATCH`、`PANDAGO_ORDER_TIMEOUT_SCHEDULE`、`PANDAGO_ORDER_TIMEOUT_DELAY`。

---

## 14. 阶段 10 验证补充说明

阶段 10 不新增业务接口，仅补充质量验证；对 API 的影响仅有上文错误码表的语义明确
（未知路径 404、500 兜底不泄露细节）。以下为配套信息：

- **测试账号**（仅开发环境，来自 `database/demo-stage4-users.sql`）：
  `merchant_demo` / `user_demo` / `admin_demo`，初始密码 `Panda@12345`；
  注册接口 `POST /auth/register` 仅创建 `USER` 角色，不接受提权字段。
  生产环境不预置任何带已知密码的账号。
- **端到端演示**：`docs/e2e_demo.py` 通过真实 HTTP 走完整链路（注册/浏览/加购/下单/支付/发货/确认/
  评价/审核/越权），环境变量同上，实测 22/22 步通过。
- **部署后接口地址**：容器内前端经 Nginx 将 `/api` 反代到 `backend:8080`；
  宿主机默认前端 `http://localhost/`、后端 `http://localhost:8080/api/v1`。
  验证健康：`GET /api/v1/health` 返回 `data.status=UP`。
- **测试与验收记录**：见 `docs/STAGE10_TESTS.md`、`docs/FINAL_ACCEPTANCE.md`。

---

## 15. 个人资料与头像（任意已登录用户）

用户 ID、用户名、角色由后端管理，以下接口**不接收 userId**，身份只从 JWT 解析，
因此用户只能读写自己的资料。

### 15.1 `GET /users/me/profile` — 查询当前用户资料

完整路径 `GET /api/v1/users/me/profile`。返回 `UserInfoVO`：
`userId / username / nickname / avatar / phone / roles` 等；**任何情况都不返回密码字段**。
头像未上传时 `avatar` 字段缺省（Jackson 省略 null），前端显示默认头像 `/images/default-avatar.svg`。

### 15.2 `PUT /users/me/profile` — 修改昵称与手机号

完整路径 `PUT /api/v1/users/me/profile`，`Content-Type: application/json`。

```json
{ "nickname": "熊猫买家", "phone": "13712345678" }
```

- 白名单字段只有 `nickname`、`phone`；夹带 `userId / username / password / status / roles` 一律忽略。
- `nickname` 必填、非纯空格、最长 50；`phone` 可留空或传 `""` 清空，非空时必须匹配 `^1[3-9]\d{9}$`。
- 手机号传空字符串时后端用 `LambdaUpdateWrapper.set(null)` 显式写空，不会因 MyBatis-Plus 忽略 null 而保留旧值。
- 项目未接入短信验证码，**不涉及验证码**，仅做格式校验。
- 成功返回更新后的 `UserInfoVO`；失败返回 400。

### 15.3 `POST /users/me/avatar` — 上传头像（multipart）

完整路径 `POST /api/v1/users/me/avatar`，`Content-Type: multipart/form-data`，文件字段名固定为 `file`。

```bash
curl -X POST http://localhost:8080/api/v1/users/me/avatar \
  -H "Authorization: Bearer <JWT>" \
  -F "file=@avatar.png"
```

响应：

```json
{ "code": 200, "success": true, "data": { "avatarUrl": "/uploads/avatars/5c78d47b815d4dbdb69d21f659daf101.png" } }
```

服务端校验规则（前端预校验只改善体验，不能替代服务端校验）：

| 项目 | 规则 |
| --- | --- |
| 文件大小 | 非空且 ≤ 2MB（2097152 字节），超限返回 400 |
| 扩展名 | 仅 `jpg / jpeg / png / webp`，`jpg` 统一规范化为 `jpeg` |
| Content-Type | 必须与扩展名匹配：`image/jpeg`（或 `image/jpg`）、`image/png`、`image/webp` |
| 文件头魔数 | 读取真实字节判断 JPG `FF D8 FF` / PNG `89 50 4E 47 0D 0A 1A 0A` / WEBP `RIFF....WEBP`；扩展名、类型、魔数三者必须一致 |
| 拒绝项 | GIF、SVG（可能携脚本）、`MZ` 开头的可执行文件、改后缀伪装、空文件、缺扩展名、路径穿越文件名 |
| Spring 上限 | `spring.servlet.multipart.max-file-size/max-request-size=3MB`，略高于业务上限以便返回清晰的 400 而非 500 |

存储与访问：

- 文件写到 `uploads/avatars/`，文件名是 32 位 UUID 十六进制 + 规范化扩展名；**不使用原始文件名**，天然防重名覆盖与路径穿越，落盘前再做 `target.startsWith(avatarDir)` 防御性校验。
- 数据库 `user.avatar` **只保存相对 URL** `/uploads/avatars/xxx.ext`，不存 Base64、不存磁盘绝对路径；写库失败会删除刚落盘的文件，避免孤儿文件。
- Spring 静态资源映射 `/uploads/** -> {uploads 目录}`，`SecurityConfig` 放行该路径（只用于展示图片，无需登录）。前端 Vite 开发服务器把 `/uploads` 代理到 `http://127.0.0.1:8080`。
- 可配置：`PANDAGO_UPLOAD_BASE_DIR` 改存储根目录，`pandago.upload.max-avatar-bytes` 改字节上限。

### 15.4 专项测试

`backend/src/test/java/com/pandago/UserAvatarUploadTests.java` 共 12 项，覆盖三种格式成功、
重复上传不覆盖、超大文件、GIF/SVG/EXE 伪装、扩展名与魔数不一致、空文件、未登录 401、
夹带 `userId` 无效、落盘与数据库 URL 一致、静态资源可访问。运行方式：

```bash
cd backend
PANDAGO_TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/pandago?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false' \
PANDAGO_TEST_DB_USER=root PANDAGO_TEST_DB_PASSWORD='' \
mvn -Dtest=UserAvatarUploadTests test
```
