# 阶段 6 测试报告：订单创建与事务管理

> 测试日期：2026-11-17。环境：本地隔离 MySQL 8.0.22（127.0.0.1:3307，库 `pandago`）、
> JDK 17、Spring Boot 3、后端 8080、前端 5173。测试用 JWT 直接签发，不依赖固定账号密码。

## 1. 用户指定的 12 项场景

| # | 场景 | 实现方式 | 真实结果 |
| --- | --- | --- | --- |
| 1 | 正常创建订单 | 直购 2 件 ¥199 商品 | ✅ 200，订单号非空，总额 ¥398，状态 PENDING_PAYMENT(待支付)，库存减 2，明细单价/小计为快照 |
| 2 | 商品不存在 | productId=999999999 | ✅ 404 |
| 3 | 商品下架 | 插入 OFF_SALE 商品 | ✅ 409 |
| 4 | 商品未审核 | 插入 PENDING 商品 | ✅ 409 |
| 5 | 库存不足 | 库存 1，购买 5 件 | ✅ 409，库存保持 1 未被扣减 |
| 6 | 地址不属于当前用户 | 用 USER_B 的地址下单 | ✅ 404 |
| 7 | 商品跨商家下单 | merchant 1 + merchant 2 商品 | ✅ 409 |
| 8 | 前端篡改价格 | 请求夹带 price/totalAmount/payAmount=0.01、userId=他人 | ✅ 以数据库价 ¥599 入账；订单归属当前用户，未生成他人订单 |
| 9 | 主表写入失败回滚 | `@MockBean` 让 `beforeOrderInsert` 抛异常 | ✅ 500，库存未变，orders/order_item 无残留 |
| 10 | 明细写入失败回滚 | 让 `beforeOrderItemInsert` 抛异常 | ✅ 500，库存与订单主表均回滚 |
| 11 | 扣库存后失败回滚 | 让 `afterStockDeduct` 抛异常 | ✅ 500，库存恢复原值 |
| 12 | 查询其他用户订单 | USER_B 访问 USER_A 订单详情 | ✅ 404；本人访问 200 且读到地址/商品快照 |

## 2. 补充场景（4 项）

| 场景 | 真实结果 |
| --- | --- |
| 购物车结算清理 | 提交 c1、不提交 c2 → 仅 c1 被删除，c2 保留；总额按购物车数量计算 ✅ |
| 快照不随商品变化 | 下单后把商品改名并改价 ¥999，详情仍显示原商品名与原总额 ¥15 ✅ |
| 分页与状态筛选 | 2 笔订单分页 total=2；筛选 COMPLETED 返回 0 ✅ |
| 未登录访问 | GET /orders、POST /orders 均 401 ✅ |

## 3. 事务验证的关键做法

三个回滚用例通过 `OrderFaultHook`（默认空实现的教学测试接缝）在精确步骤抛运行时异常，
然后**在同一测试里直接用 JdbcTemplate 查数据库**确认：
- 库存 `stock` 回到操作前数值（证明条件 UPDATE 被回滚）；
- `orders` / `order_item` 记录数为 0（证明已写入的行被撤销）。

测试类**不使用** `@Transactional`：外层测试事务会让 Spring 把服务层事务合并进来，
导致"回滚后查库"看到未提交的中间状态，无法正确验证回滚。每个用例用随机商品名与专用 userId
（92001/92002），`@AfterEach` 统一清理订单、明细、购物车、地址与商品。

## 4. 命令与汇总

```bash
cd backend && mvn test          # 全量测试
cd backend && mvn clean package # 清理打包 + 全量测试
cd frontend && npm run build    # 类型检查 + 构建
```

- `mvn test`：**65/65 通过**（基础 2、目录 8、认证 12、阶段 4 14、阶段 5 13、阶段 6 16），失败 0、错误 0。
- 阶段 1–5 用例仍全绿，说明订单模块未破坏既有功能。
- 前端 `npm run build` 成功（`vue-tsc --noEmit && vite build`），仅有 Vite 单包体积 >500 kB 的提示。

## 5. HTTP 端到端联调（真实后端进程）

```
未登录 GET /orders → 401
GET /addresses → 0 条（联调账号），新建地址 → 200
GET /products 取商品 4（¥299） → 200
POST /orders 夹带 price/totalAmount=0.01、userId=他人 → 200，订单号 PO…，总额 ¥598（=299×2）
GET /orders → total=1；GET /orders/{id} → 200，读到地址与商品快照
他人访问订单详情 → 404；不存在的商品下单 → 404
联调数据已清理：orders=0、order_item=0、商品 4 库存恢复 60
```

## 6. 前端渲染检查

- 新增组件：`CheckoutView`（地址单选、商品确认、备注、预估金额、提交）、`OrderSuccessView`、
  `OrderListView`（状态筛选、分页）、`OrderDetailView`（快照展示）；购物车"去结算"已接入结算页。
- 无头 Chrome 访问 `/orders`：未登录被路由守卫重定向到登录页（预期行为）。
- 四个新组件与 `/orders`、`/checkout` 路由均返回 200；首单金额标注"预览"，成功页说明以后端金额为准。
