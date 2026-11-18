# 阶段 8 测试说明：发货、确认收货、评价与管理端

> 测试类：`backend/src/test/java/com/pandago/Stage8FulfillmentTests.java`（15 项）
> 运行方式见文末。事务与并发用例**不使用类级 `@Transactional`**，真实提交后查库验证。

## 环境准备

```bash
# 1. 执行数据库迁移（可重复执行）
mysql --default-character-set=utf8mb4 < database/migration/V5__stage8_shipment_review_admin.sql

# 2. 运行阶段 8 测试
cd backend
PANDAGO_TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/pandago?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false' \
PANDAGO_TEST_DB_USER=root PANDAGO_TEST_DB_PASSWORD= \
mvn -Dtest=Stage8FulfillmentTests test
```

测试用专用 userId（94101~94301）与随机商品名（`S8-` 前缀），`@AfterEach` 清理全部订单、
明细、支付、物流、评价、操作日志与商品，不污染初始化数据。

## 用户指定的 13 项场景

| # | 场景 | 对应用例 | 预期 | 结果 |
| --- | --- | --- | --- | --- |
| 1 | 普通用户访问商家接口 | `normalUserCannotAccessMerchantApi` | 403 | ✅ |
| 2 | 商家操作其他商家订单 | `merchantCannotShipOthersOrder` | 403，订单仍待发货、无物流 | ✅ |
| 3 | 未支付订单发货 | `cannotShipUnpaidOrder` | 409，状态机拒绝 | ✅ |
| 4 | 正常发货 | `shipSuccessfully` | 200，待收货，物流 1 条，无轨迹字段 | ✅ |
| 5 | 用户确认他人订单 | `userCannotConfirmOthersOrder` | 404，状态不变 | ✅ |
| 6 | 待发货订单确认收货 | `cannotConfirmBeforeShipment` | 409 | ✅ |
| 7 | 正常确认收货 | `confirmReceiptSuccessfully` | 200，已完成，写入 received_at | ✅ |
| 8 | 重复确认收货 | `duplicateConfirmRejected` | 409，无副作用 | ✅ |
| 9 | 未完成订单评价 | `cannotReviewIncompleteOrder` | 409，无评价记录 | ✅ |
| 10 | 重复评价 | `duplicateReviewRejected` | 409，仅 1 条评价 | ✅ |
| 11 | 商家自行审核商品 | `merchantCannotAuditProduct` | 403 | ✅ |
| 12 | 普通用户访问管理员接口 | `normalUserCannotAccessAdminApi` | 用户列表/订单列表均 403 | ✅ |
| 13 | 管理员审核流程和状态变更 | `adminAuditRecordsReviewerAndLog` | 200，状态驳回、下架、审核人/时间/意见与日志齐全 | ✅ |

## 补充场景（2 项）

| # | 场景 | 对应用例 | 预期 | 结果 |
| --- | --- | --- | --- | --- |
| 14 | 完整链路 + 评价列表公开 | `fullFulfillmentAndPublicReviewList` | 发货→确认→评价成功，游客可查商品评价列表 | ✅ |
| 15 | 商家订单列表隔离 | `merchantOrderListScoped` | 只返回本商家订单，total=1 | ✅ |

## 数据库断言亮点

测试不只看 HTTP 状态码，还直接查库确认最终一致性：

- `shipment` 记录数：发货成功 1 条、越权/未支付 0 条。
- `orders.status` 与 `received_at`：确认收货后为 `COMPLETED` 且收货时间非空。
- `review` 记录数：重复评价后仍为 1 条（唯一索引 `uk_review_order_item`）。
- `product.audit_by` / `audit_time` / `audit_reason`：管理员审核后正确写入。
- `operation_log`：审核操作生成 1 条 `PRODUCT/AUDIT` 日志。

## 与并发/幂等的关系

本阶段的状态流转与阶段 7 支付/取消共享同一模型：**状态机校验意图 + 条件 UPDATE 抢变更权 + 唯一索引兜底**。
条件 UPDATE（`shipOrder` / `confirmReceipt`）的 `WHERE` 带身份列与源状态，重复或并发请求第二次影响 0 行，
直接返回 409，不会重复写物流、收货时间或评价。评价用 `uk_review_order_item` 兜住并发重复提交。

## 全量回归

```bash
mvn clean package
```

结果：**92 项测试全部通过**（上下文/健康 2 + 分类商品 8 + 认证 12 + 阶段 4 14 + 阶段 5 13 +
阶段 6 16 + 阶段 7 12 + 阶段 8 15），生成 `pandago-backend-0.0.1-SNAPSHOT.jar`。
前端 `npm run build`（vue-tsc 类型检查 + Vite 构建）通过。

## HTTP 联调记录

- 健康检查 `GET /api/v1/health` → 200。
- 未登录访问商家订单、确认收货、评价、管理端用户/订单 → 均 401（被安全链拦截）。
- 公开商品评价列表 `GET /api/v1/reviews/products/{id}` → 200（游客可看评价，符合 PRD）。
- 前端新路由 `/merchant/orders`、`/admin/users`、`/admin/categories`、`/admin/products`、
  `/admin/orders`、`/reviews` → 均 200 可达。

> 带登录凭据的完整发货/评价链路已由同栈 MockMvc 集成测试覆盖（真实 SecurityContext + JWT），
> 认证后的浏览器手动联调因环境的敏感信息审批限制未执行，此处如实说明，不声称已完成。
