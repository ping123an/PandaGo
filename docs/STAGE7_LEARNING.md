# 阶段 7 教学重点：状态机、幂等性与事务一致性

> 本文对应 `order` / `payment` 模块实现。测试与接口见 `STAGE7_TESTS.md`、`API.md`。
> 本阶段**没有重写阶段 6 的订单创建逻辑**；新增的是状态流转、支付记录和取消回补库存。

## 1. 状态机：把“能做什么”集中到一个地方

订单有五个状态，合法流转如下：

| 当前状态 | 允许操作 | 合法目标状态 |
| --- | --- | --- |
| `PENDING_PAYMENT` 待支付 | 支付、用户取消、超时关闭 | `PENDING_SHIPMENT`、`CANCELLED` |
| `PENDING_SHIPMENT` 待发货 | 商家发货（阶段 8） | `PENDING_RECEIPT` |
| `PENDING_RECEIPT` 待收货 | 用户确认收货（阶段 8） | `COMPLETED` |
| `COMPLETED` 已完成 | 无（可评价是另一业务） | — |
| `CANCELLED` 已取消 | 无 | — |

项目里由 `OrderStatusMachine` 统一维护这张表：

```java
OrderStatusMachine.assertTransition(
        order.getStatus(), OrderStatus.PENDING_SHIPMENT.getValue(), OrderOperation.PAY);
```

一次调用同时校验两件事：当前状态是否允许 `PAY`，以及 `PENDING_PAYMENT → PENDING_SHIPMENT` 是否合法。
Controller 完全没有 `setStatus`，只传订单号或订单 ID；状态值由后端状态机决定。

**为什么不用一堆 `if` 散落在 Controller？** 散落写会漏、会互相矛盾。状态机把业务规则收敛到一个文件，
未来阶段 8 的发货、确认收货和阶段 9 的超时关闭都复用同一处规则。

## 2. 支付调用链与事务边界

```
POST /api/v1/payments/mock  { orderNo }
  → PaymentController            只做参数绑定
  → PaymentServiceImpl           当前用户 → 按订单号+用户查单 → 状态机校验
  → PaymentTransactionService    @Transactional 事务开始
       1. 事务内重新读取订单最新状态
       2. 再次状态机校验（防止校验后被取消）
       3. payment_record INSERT（payment_no / order_id 唯一索引）
       4. 条件 UPDATE orders：PENDING_PAYMENT → PENDING_SHIPMENT
       5. 受影响行数必须为 1；否则抛异常回滚
  → 正常返回：事务提交；抛异常：支付记录与订单状态一起回滚
```

两个细节很重要：

1. **金额只从 `orders.total_amount` 读**。`MockPaymentDTO` 根本没有金额字段，
   请求里夹带 `amount: 0.01` 会被 Jackson 忽略。服务端不会把前端传来的数字当作实付金额。
2. **事务放在独立 Bean**。如果 `pay()` 和校验方法写在同一个类里互相调用，Spring AOP 的自调用陷阱会让
   `@Transactional` 不生效。本阶段沿用阶段 6 的拆分：`Service`（校验）→ `TransactionService`（写事务）。

## 3. 幂等性：同一个请求做一次和做多次，结果不能叠加

幂等不是“按钮变灰”，也不是“接口返回 200”。它要求：**重复请求不会产生额外副作用**。

本项目对支付用了三道防线：

| 防线 | 解决什么 | 代码位置 |
| --- | --- | --- |
| 状态机 | 已支付订单不允许再执行 `PAY` | `OrderStatusMachine` |
| 唯一索引 | 两个并发请求同时插入支付记录时，数据库只放一条 | `uk_payment_no`、`uk_payment_order` |
| 条件 UPDATE | 只有仍是 `PENDING_PAYMENT` 的订单才能改成 `PENDING_SHIPMENT` | `OrderMapper.xml` |

重复支付返回 **409 + 明确业务提示**（含原支付流水号），而不是静默成功或 500。
这既让调用方知道“已经付过了”，又保证数据库里只有一条支付记录、订单只流转一次。

## 4. 唯一索引与条件 UPDATE 的区别

- **唯一索引**是数据库级的“最后一道闸门”：即使 Java 判断全错，重复数据也插不进去。
  它防的是“重复记录”。
- **条件 UPDATE**是数据库级的“抢锁UPDATE”：只有满足 `status = 预期值` 的行才能被改。
  它防的是“重复变更状态”。

两者作用不同，不能互相替代。举例：只靠唯一索引，第二次请求可能拿到异常但订单状态已被其他路径改坏；
只靠条件 UPDATE，若并发时两笔支付流水号不同，仍可能产生两条支付记录。

## 5. 取消订单：先抢状态，再回库存

```
POST /api/v1/orders/{id}/cancel
  → 当前用户 + 订单归属校验
  → 状态机：仅 PENDING_PAYMENT 允许 CANCEL_BY_USER
  → @Transactional
       UPDATE orders SET status='CANCELLED'
       WHERE id=? AND user_id=? AND status='PENDING_PAYMENT'
       rows == 1 ? 继续 : 抛 409（绝不平库存）
       按 order_item 快照逐项 UPDATE product SET stock = stock + quantity WHERE id = ?
       任一回补失败 → 整个事务回滚，订单仍是待支付
```

所以“重复取消”和“并发取消”都只有第一个请求能拿到状态变更权。后续请求影响 0 行，直接 409，
库存不会多出来。已支付订单处于 `PENDING_SHIPMENT`，普通取消接口同样被状态机拒绝；退款不在本项目范围内。

## 6. 事务一致性：支付与取消各自都是原子操作

- 支付：`payment_record INSERT + orders 状态 UPDATE` 同事务。
  测试中用 `PaymentFaultHook.afterPaymentInsert()` 在两步之间抛异常，最终支付记录 0 条、订单仍待支付。
- 取消：`orders 状态 UPDATE + product.stock 回补` 同事务。
  如果任何商品回补失败，订单状态也会回滚，不会出现“订单取消了但库存没还”。

注意：`@Transactional` 只保证原子性，不保证并发正确。并发仍要靠唯一索引和条件 UPDATE；
这正是阶段 6 学过的结论在状态流转场景的复用。

## 7. 前端按钮防重复点击 ≠ 后端幂等

| 维度 | 前端防重复点击 | 后端幂等 |
| --- | --- | --- |
| 实现 | `paying` / `payingIds` 让按钮 loading、禁用 | 状态机 + 唯一索引 + 条件 UPDATE + 事务 |
| 作用范围 | 只保护当前浏览器、当前页面实例 | 保护所有客户端：浏览器、Postman、脚本、并发请求 |
| 能否被绕过 | 能。刷新页面、开两个标签页、改 JS、直接调接口都可绕过 | 不能绕过；请求最终都要过服务端和数据库 |
| 失败后行为 | 按钮恢复可点，用户可能再点一次 | 重复请求得到稳定结果与明确业务码 |
| 定位 | 用户体验优化 | 正确性与数据安全底线 |

因此前端要防重（避免用户困惑），后端必须幂等（防止重复支付/重复回补库存）。
**两者不能互相替代**：前端防重是“减少重复请求”，后端幂等是“即使重复请求也安全”。

## 8. 常见错误与排查

| 现象 | 可能原因 | 排查方法 |
| --- | --- | --- |
| 第二次支付返回 500 | 唯一键异常没有转换 | 看日志是否有 `DuplicateKeyException`；确认 Service 捕获并返回 409 |
| 支付记录存在但订单仍待支付 | 两步不在同一事务，或自调用导致 `@Transactional` 失效 | 检查事务方法是否在独立 Bean；用故障注入验证回滚 |
| 重复取消库存增加 | 先查状态再无条件 UPDATE，或先回库存再改状态 | 检查 `transitionStatus` 受影响行数是否被判断 |
| 他人订单可被支付/查询 | 查询条件只有 orderNo / id，没有 `user_id` | 所有订单查询都必须带当前用户 ID |
| 前端显示已支付但数据库未变 | 前端提前提交状态，或只信接口返回不刷新 | 支付成功后重新查询订单状态/支付结果 |

## 9. 练习（先自己做，再看提示）

### 基础题

1. 为什么支付接口不接收 `amount` 字段？前端展示的金额从哪来？
2. `uk_payment_order(order_id)` 和 `uk_payment_no(payment_no)` 分别防什么？
3. 为什么取消订单要先更新状态、后恢复库存，而不是反过来？

### 代码阅读题

1. 在 `PaymentServiceImpl.mockPay` 中找到捕获 `DuplicateKeyException` 的分支，
   说明为什么捕获后要回查支付记录。
2. 阅读 `OrderMapper.xml` 的 `transitionStatus`，Explain 为什么 `user_id` 也要放进 WHERE。
3. 阅读 `OrderStatusMachine.allowedOperations`，思考阶段 8 增加“商家发货”时要改哪些地方。

### 独立编码练习

给 `GET /api/v1/payments/orders/{orderNo}` 增加一个“未支付订单也返回应付金额”的前端展示优化：
在支付页区分 `应付金额` 与 `实付金额`，并确认前端没有参与任何金额计算。提示：后端 `PaymentVO` 已经提供 `paid` 字段。

### 面试题

1. 你如何实现“支付接口幂等”？请按状态机、唯一索引、条件更新、事务四层说明。
2. 前端按钮防抖/防重和后端幂等有什么区别？为什么不能只靠前端？

## 10. 面试时如何介绍本阶段

> 我在订单状态机上收敛了五态流转规则，支付和取消都先做状态校验，再用数据库条件 UPDATE 抢状态变更权。
> 模拟支付按订单号查单、校验归属和状态，金额只从订单快照读取；支付记录与订单状态更新在同一事务，
> `payment_no` 和 `order_id` 的唯一索引兜底幂等。取消订单只有拿到 `PENDING_PAYMENT → CANCELLED`
> 的状态变更权后才回补库存，因此重复和并发取消都不会多回库存。前端按钮 loading 只是体验优化，
> 后端幂等才是数据安全底线。
