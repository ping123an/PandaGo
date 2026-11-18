# 阶段 7 测试报告：订单状态、模拟支付、取消与幂等

> 测试日期：2026-11-17。环境：本地 MySQL 8.0.22（127.0.0.1:3307，库 `pandago`）、JDK 17、
> Spring Boot 3.2.5、MockMvc 集成测试、后端 8080、前端 5173。阶段 6 的订单创建与条件扣库存逻辑未改动。

## 1. 用户指定的 11 项场景

| # | 场景 | 实现方式 | 真实结果 |
| --- | --- | --- | --- |
| 1 | 正常支付 | 支付 ¥199×2 订单，请求夹带 `amount=0.01` | ✅ 200；流水号 `PAY…`，实付 ¥398，状态 `PENDING_SHIPMENT`，支付时间非空；库存未二次变化 |
| 2 | 重复支付 | 同一订单连续支付两次 | ✅ 第二次 409，提示“订单已支付成功，请勿重复支付”，支付记录仍为 1 条 |
| 3 | 两个并发支付 | 2 线程 + `CountDownLatch` 同时支付 | ✅ 1 个 200、1 个 409；支付记录 1 条，订单只流转一次 |
| 4 | 支付已取消订单 | 先取消再支付 | ✅ 409；状态仍为 `CANCELLED`，无支付记录 |
| 5 | 支付其他用户订单 | USER_B 支付 USER_A 订单号 | ✅ 404；订单仍为 `PENDING_PAYMENT`，无支付记录 |
| 6 | 正常取消待支付订单 | 取消 3 件商品订单 | ✅ 200；状态 `CANCELLED`，库存从 `initial-3` 恢复到 `initial` |
| 7 | 重复取消 | 连续取消两次 | ✅ 第二次 409；库存保持恢复后的值 |
| 8 | 并发取消 | 2 线程同时取消 | ✅ 1 个 200、1 个 409；库存只恢复一次 |
| 9 | 取消后库存只恢复一次 | 连续取消 3 次后查最终库存 | ✅ 最终库存 = 初始库存，精确恢复一次 |
| 10 | 已支付订单不能普通取消 | 支付后调用取消接口 | ✅ 409；状态仍为 `PENDING_SHIPMENT`，支付记录保留 |
| 11 | 支付事务回滚 | `PaymentFaultHook.afterPaymentInsert` 抛异常 | ✅ 500；支付记录 0 条，订单仍 `PENDING_PAYMENT` |

## 2. 补充场景（1 项）

| 场景 | 真实结果 |
| --- | --- |
| 状态查询 / 支付结果查询归属校验 | ✅ 本人查询 200，`canPay/canCancel` 正确；他人查询 404；支付后 `canPay=false`、支付结果 `paid=true` |

## 3. 并发与幂等的关键做法

- `payment_record` 上有 `uk_payment_no(payment_no)` 和 `uk_payment_order(order_id)`。并发支付同时插入时，
  数据库唯一键让第二个请求失败；Service 捕获 `DuplicateKeyException` 后回查原支付记录，返回 409 + 原流水号。
- 订单状态更新使用条件 SQL：

  ```sql
  UPDATE orders SET status = ?, updated_at = NOW()
  WHERE id = ? AND user_id = ? AND status = ?
  ```

  受影响行数为 1 才允许继续（支付场景是写支付记录后更新订单；取消场景是更新订单成功后回补库存）。
- 取消订单先在事务内抢到“待支付 → 已取消”的状态变更权，再按 `order_item` 快照逐项 `stock = stock + quantity`。
  并发取消的第二个请求拿不到状态变更权，直接 409，绝不回补库存。
- 支付事务与取消事务都标注 `@Transactional(rollbackFor = Exception.class)`，且放在独立 Bean 中，避免同类自调用导致注解失效。
- 本阶段只恢复 `stock`，不反向调整 `sales`；这是保持阶段 6 已验收销量口径不变的有意选择。

## 4. 命令与汇总

```bash
# 迁移（可重复执行）
mysql --default-character-set=utf8mb4 < database/migration/V4__stage7_payment.sql

cd backend && mvn clean package      # 全量测试 + 打包
cd frontend && npm run build         # vue-tsc --noEmit && vite build
```

- `mvn clean package`：**77/77 通过**（基础 2、目录 8、认证 12、阶段 4 14、阶段 5 13、阶段 6 16、阶段 7 12），
  Failures 0、Errors 0；产物 `backend/target/pandago-backend-0.0.1-SNAPSHOT.jar`。
- 前端 `npm run build` 成功（仅 Vite 单包体积 >500 kB 的既有提示）。
- 阶段 1–6 用例仍全绿：新增状态机、支付表和条件 UPDATE 未破坏已验收的订单创建逻辑。

## 5. 真实后端进程与前端路由检查

```
GET  /api/v1/health                         → 200，status=UP
POST /api/v1/payments/mock            未登录 → 401
GET  /api/v1/payments/orders/{orderNo} 未登录 → 401
POST /api/v1/orders/{id}/cancel       未登录 → 401
GET  /api/v1/orders/{id}/status       未登录 → 401
GET  http://127.0.0.1:5173/pay/{orderNo}    → 200（前端支付页路由可达）
```

说明：带登录凭据的真实进程联调命令被当前环境的敏感信息审批拦截，未继续执行；认证后的支付/取消全链路
由 `Stage7OrderStatusTests` 的 MockMvc 集成测试覆盖，使用的是同一套 Spring Security、Controller、Service、
Mapper 和 MySQL，不是纯 Mockito 单元测试。
