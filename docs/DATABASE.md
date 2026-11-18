# PandaGo 数据库设计说明（阶段 2）

> 对应脚本：`database/schema.sql`、`database/init-data.sql`

## 1. 与文档的对齐与补充

| 来源 | 要求 | 本阶段落实 |
| --- | --- | --- |
| 用户指令 | 数据库名 `pandago`，utf8mb4 | ✅ |
| PRD 5.4 | BIGINT 主键、DECIMAL 金额、整数库存、15 张核心表 | ✅ 本阶段先建 6 张（用户指定），其余按阶段推进 |
| AGENTS 五 | InnoDB、逻辑删除、唯一约束、必要索引、禁止明文密码 | ✅ |
| AGENTS 6.5 | 状态值 `ON_SALE` / `APPROVED` | ✅ 商品用字符串枚举字段 |

**重要说明（文档缺口）**：`TECH_DESIGN.md` 第四章"数据库设计"在原文第 416 行结束于"数据库引擎："的空代码块，
缺少所有表的字段、类型、默认值、索引和状态值定义；且该章写的数据库名为 `shop`。

因此本次采用**最小补充**：以用户指令的 `pandago` 为准，字段设计仅覆盖阶段 2 六张表的必需信息，
依据 PRD/AGENTS 的明确规则推导，不擅自增加优惠券、促销、物流等后续阶段功能字段。
补充内容集中记录在本文件，后续若 TECH_DESIGN 补全，以其为准同步调整脚本与 Entity。

## 2. 表关系

```
role ──< user_role >── user        （多对多：一个用户可有多角色）
category ──< category              （一对多：两级分类，parent_id=0 为一级）
category ──< product               （一对多：一个分类多个商品）
product  ──< product_image         （一对多：商品多图）
```

- 第一阶段按用户要求创建 6 张表；购物车、地址、订单等表在对应阶段创建。
- 不添加物理外键约束：分阶段建表时引用表可能尚未存在；关联完整性由 Service 层校验 + 索引保证。

## 3. 字段要点

### user
- `username` 唯一索引 `uk_user_username`：登录账号不可重复。
- `password_hash` 存 BCrypt 哈希，禁止明文；阶段 2 不写入任何用户数据。
- `status` TINYINT（1 启用 / 0 禁用），登录时校验（阶段 3）。
- `deleted` 逻辑删除，避免物理删除历史数据。

### role / user_role
- `role.code` 唯一索引，受控枚举 `USER` / `MERCHANT` / `ADMIN`；普通用户注册不能自选角色。
- `user_role` 唯一索引 `(user_id, role_id)` 防止重复授权；`idx_user_role_role` 支持按角色反查用户。

### category
- `parent_id = 0` 表示一级分类，实现两级结构；`uk_category_name_parent` 防止同级重名。
- `idx_category_parent (parent_id, status, sort)` 支撑"按父分类取启用子分类并排序"的高频查询。
- `status` 禁用后不允许新增该分类商品（阶段 4 校验）。

### product
- `price DECIMAL(10,2)`，`stock INT`，符合 PRD 金额/库存规范。
- `status VARCHAR(20)`：`ON_SALE` / `OFF_SALE`；`audit_status`：`PENDING` / `APPROVED` / `REJECTED`。
  用户端只展示 `status='ON_SALE' AND audit_status='APPROVED' AND deleted=0`。
- `merchant_id BIGINT DEFAULT 0`：阶段 2 无商家表，0 表示平台演示商品；后续必须由后端按登录身份赋值。
- 索引：`idx_product_category` 分类筛选、`idx_product_status` 状态过滤、`idx_product_price` 价格排序、
  `idx_product_sales` 销量排序；`uk_product_category_name` 同时保证初始化脚本可重复执行。
- 关键词搜索使用 `LIKE '%kw%'`（前置通配符无法命中 B-tree 索引），数据量增长后再评估全文索引。

### product_image
- `uk_product_image_pair (product_id, url)` 防重复；`idx_product_image_product (product_id, sort)` 支撑按序取图。

## 4. 初始化脚本的可重复执行性

- `schema.sql`：全部 `CREATE TABLE IF NOT EXISTS`，**不 DROP、不清空已有数据**。
- `init-data.sql`：全部 `INSERT IGNORE` / `INSERT IGNORE ... SELECT`，靠唯一键跳过重复记录，
  **不覆盖**已修改的分类名、商品价格或状态。
- 结论：两个脚本可反复执行，不会清空或覆盖已有业务数据。
- 初始化数据：3 个角色、6 个一级分类、7 个二级分类、10 个已审核上架商品、10 条商品图片记录。
- 图片使用项目内 `frontend/public/images/panda-product.svg` 占位，不引用第三方品牌素材。

## 5. 后续阶段的表

按 PRD 5.4 顺序：阶段 5 `cart_item` / `user_address` / `favorite`；阶段 6 `orders` / `order_item`；
阶段 7 `payment_record`；阶段 8 `shipment` / `review`；阶段 9+ `announcement` / `operation_log`。

## 6. 阶段 4 增量变更

- 新增脚本：`database/migration/V1__stage4_product_audit.sql`，通过 `information_schema` 判断后幂等执行。
- `product` 新增 `audit_reason VARCHAR(255)`：保存管理员驳回原因；审核通过时清空。
- `product` 新增 `idx_product_merchant(merchant_id, status, audit_status)`：支撑商家「我的商品」按身份 + 状态过滤。
- `schema.sql` 已同步更新，全新库直接包含上述列和索引；已有库执行迁移脚本即可。
- 阶段 4 图片仍按原设计保存：`product.main_image` + `product_image(product_id, url, sort)`。
  `sort=1` 为主图，`sort>=2` 为详情图；未新增对象存储字段，也未实现文件上传。

## 7. 阶段 5 增量变更

- 新增脚本：`database/migration/V2__stage5_cart_favorite_address.sql`，全部 `CREATE TABLE IF NOT EXISTS`，
  可重复执行；`schema.sql` 已同步，新库直接包含三张新表。执行前后已有业务数据不变。
- `cart_item(user_id, product_id, quantity, selected, …)`：唯一索引 `uk_cart_user_product` 保证
  同一用户同一商品一行；表内**不保存价格**；用物理删除避免软删除行占用唯一键。
  索引 `idx_cart_user(user_id, selected)` 支撑「按用户 + 勾选状态」查询。
- `favorite(user_id, product_id, created_at)`：唯一索引 `uk_favorite_user_product` 防止重复收藏；
  同样物理删除。
- `user_address`：字段含收货人、11 位手机号、省市区、详细地址、`is_default`、`deleted`。
  生成列 `default_flag = CASE WHEN is_default=1 THEN user_id ELSE NULL END` + 唯一索引
  `uk_address_user_default` 保证同一用户最多一个默认地址；逻辑删除前先把 `is_default` 置 0。
- 删除默认地址后**不自动指定新默认**（PRD 未规定）；理由与接口行为见 `API.md` 第 7.4 节。
- 关联查询：`cart_item` / `favorite` 均从 `product` 实时读取价格、图片、状态，采用
  「批量查 + 内存 Map」避免 N+1；不保存价格快照，快照属于阶段 6 订单表职责。

## 8. 阶段 6 增量变更

- 新增脚本：`database/migration/V3__stage6_orders.sql`，`CREATE TABLE IF NOT EXISTS` 可重复执行；
  `schema.sql` 已同步两张新表。表：`orders`（订单主表）、`order_item`（订单明细）。
- `orders`：订单号 `order_no` 唯一索引 `uk_orders_no`（后端生成，形如 `PO` + 时间戳 + 随机串）；
  金额 `DECIMAL(10,2)`；状态字符串默认 `PENDING_PAYMENT`，按 AGENTS 6.6 五态流转；
  收货人/电话/省市区/详细地址均为**下单快照**；索引 `idx_orders_user(user_id, created_at)` 支撑我的订单分页。
- `order_item`：商品名称、主图、单价、数量、小计均为**下单快照**；索引 `idx_order_item_order(order_id)`
  支撑按订单批量取明细；不建物理外键，引用完整性由 Service 层保证。
- 订单表不设 `deleted` 字段：交易历史通过状态流转，不因普通用户操作物理删除（AGENTS 五）。
- `product` 未新增字段；库存扣减通过 `ProductMapper.xml` 的 `deductStock` 条件 UPDATE 完成，
  同时校验 `stock >= 数量`、`status='ON_SALE'`、`audit_status='APPROVED'`、`deleted=0`，
  受影响行数为 0 即失败（AGENTS 6.5）。
- 购物车结算成功后仅物理删除本次提交的 `cart_item` 行，条件带 `user_id` 防止误删他人数据。

## 9. 阶段 7 增量变更

- 新增脚本：`database/migration/V4__stage7_payment.sql`，`CREATE TABLE IF NOT EXISTS` 可重复执行；
  `schema.sql` 已同步新增 `payment_record`。阶段 7 不修改阶段 6 已建的表结构。
- `payment_record`：`payment_no VARCHAR(64)` 由后端生成，唯一索引 `uk_payment_no` 防重复流水号；
  额外唯一索引 `uk_payment_order(order_id)` 保证一单最多一条成功支付记录；
  `idx_payment_order_no(order_no)` 支撑按订单号查支付结果，`idx_payment_user(user_id, created_at)` 支撑用户维度查询。
- `pay_amount DECIMAL(10,2)` 只保存服务端从 `orders.total_amount` 读取的实付金额；
  `status` 固定 `SUCCESS`，`paid_at` 为服务端支付成功时间。交易记录不物理删除。
- 订单状态仍使用 `orders.status` 字符串，`OrderStatusMachine` 统一维护五态流转规则；
  `OrderMapper.transitionStatus` 用 `id + user_id + 源状态` 条件 UPDATE 抢状态变更权。
- 库存回补新增 `ProductMapper.restoreStock`：`UPDATE product SET stock = stock + ? WHERE id = ?`，
  只在订单状态条件更新成功后执行，且与之同事务。不附带上下架/审核条件，避免商品后续下架时吞掉应还库存。
- 本阶段**不反向调整 `sales`**：阶段 6 的条件扣库存会累加销量，取消只回补库存，
  以保持一致销量统计口径；这是有意的范围约束，不是遗漏。

## 10. 阶段 8 增量变更

- 新增脚本：`database/migration/V5__stage8_shipment_review_admin.sql`，
  `CREATE TABLE IF NOT EXISTS` + 基于 `information_schema` 的判空 ALTER，可重复执行，不 DROP、不清空。
  `schema.sql` 已同步全部变更。
- **字段缺口说明（先说明后补充）**：PRD 5.4 表清单列出了 `shipment`、`review`、`operation_log`，
  但此前未建；审核要求记录审核人/时间/意见，而 `product` 只有 `audit_status`/`audit_reason`。
  本阶段补全：`product` 增加 `audit_by`（审核人 ID）、`audit_time`（审核时间）；审核意见复用 `audit_reason`；
  `orders` 增加 `received_at`（确认收货时间）。未改动阶段 6/7 的既有字段与订单创建逻辑。
- `shipment`（物流信息表）：`order_id` 唯一索引 `uk_shipment_order` 保证一单一条物流；
  只保存 `company`（物流公司）、`tracking_no`（单号）、`shipped_at`（服务端发货时间），
  **不存轨迹数据、不展示伪造进度**（PRD 三 / AGENTS 1.2）。`order_no` 为快照便于查询。
- `review`（商品评价表）：唯一索引 `uk_review_order_item(order_item_id)` 保证同一订单商品只评一次；
  `product_id` + `created_at`、`user_id` + `created_at` 索引分别支撑商品评价列表与我的评价分页；
  `product_name` 为下单快照，评价列表可展示商品名。评分 `rating TINYINT` 业务层限制 1~5。
- `operation_log`（操作日志表）：记录 `operator_id`/`operator_name`（来自 SecurityContext）、
  `module`/`operation`/`target_type`/`target_id`/`detail`/`created_at`；
  `idx_operation_log_operator`、`idx_operation_log_target` 支撑按操作人或对象查询。
  审核、发货、确认收货、用户启停均写入日志。
- `orders` 新增 `idx_orders_merchant(merchant_id, status, created_at)` 支撑商家订单列表分页。
- 条件更新：新增 `OrderMapper.shipOrder`（`id + merchant_id + 源状态`）与
  `OrderMapper.confirmReceipt`（`id + user_id + 源状态` 并写 `received_at`），
  与阶段 7 的 `transitionStatus` 同属"状态机判定意图 + 条件 UPDATE 抢变更权"模型。
- 所有交易与评价记录不因普通用户操作物理删除；测试产生的 `S8-` 商品与 94101~94301 用户在
  `@AfterEach` 中清理，不污染初始化数据。

## 11. 阶段 9 增量变更（无 DDL）

本阶段**没有新增表或字段**，因此不提供 V6 迁移脚本。原因如下：

- **缓存**：Redis 是内存旁路存储，数据由应用按 TTL 写入并自动过期，不需要建表。
  Key 采用 `pandago:` 前缀 + `CacheKeys` 常量集中管理，与数据库解耦。
  Redis 连接串、密码全部来自环境变量（`PANDAGO_REDIS_*`），密码不入库、不进仓库。
- **超时订单**：阶段 6 的 `orders` 表已有 `created_at`，阶段 7 已有 `status` 与回补库存的
  `ProductMapper.restoreStock`；超时关闭只是新增一条条件 UPDATE
  （`OrderMapper.closeTimeoutOrder`：`id + status='PENDING_PAYMENT' + created_at<=cutoff`）
  和一条分批查询（`OrderMapper.selectTimedOutOrderIds`），均写在 `OrderMapper.xml`，无 DDL。
- 超时关闭与阶段 7 用户取消共用 `orders`/`order_item`/`product` 三表与同一套库存回补 SQL，
  未引入新的数据表，也未改动既有字段口径。

索引复用说明：`selectTimedOutOrderIds` 按 `status + created_at` 过滤并排序。
`orders` 现有 `idx_orders_user(user_id, created_at)` 与阶段 8 的 `idx_orders_merchant(merchant_id, status, created_at)`
都不以 `status` 为最左列，理论上存在全表扫描风险（阶段 9 数据量小，暂未引入）。阶段 10 已补齐该优化，见下节。

## 12. 阶段 10 增量变更（索引优化）

阶段 10 **不新增表或字段**，仅补一个索引以消除超时扫描的全表扫描风险：

```sql
-- 超时扫描：SELECT id FROM orders
--   WHERE status=? AND created_at<=? ORDER BY created_at ASC, id ASC LIMIT ?
-- 新增覆盖索引后 EXPLAIN 命中 idx_orders_status_created，Extra=Using index
KEY `idx_orders_status_created` (`status`, `created_at`, `id`)
```

- `database/schema.sql` 的 `orders` 表定义已加入该索引（新容器/新库一次到位）。
- `database/migration/V6__stage10_order_timeout_index.sql` 为已部署旧库的增量脚本，
  用 `information_schema.statistics` 判重，可重复执行（实测执行两次均成功，第二次输出 1 表示跳过）。
- 在 3307 测试库执行后 `EXPLAIN` 结果：`key=idx_orders_status_created`、`Using index`，
  确认由全表扫描变为覆盖索引扫描。
- 表关系与字段口径无变化：订单/商品/地址快照、支付唯一索引、状态流转条件 UPDATE 均保持阶段 6–9 设计。
