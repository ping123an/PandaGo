# 阶段 6 教学重点：订单、事务与库存并发

> 本文对应 `order` 模块实现，回答"为什么这样写"。测试与接口见 `STAGE6_TESTS.md`、`API.md`。

## 1. `@Transactional` 到底做了什么

Spring 用 AOP 代理包住被标注的方法：调用前开启事务（取得数据库连接、设置自动提交为 false），
方法正常返回时 `commit()`，抛出 `RuntimeException` 或 `Error` 时 `rollback()`。

本项目在 `OrderTransactionServiceImpl.commit()` 上写：

```java
@Transactional(rollbackFor = Exception.class)
public OrderCreateVO commit(OrderDraft draft) { ... }
```

- 默认只回滚运行时异常和 Error；`rollbackFor = Exception.class` 把受检异常也纳入回滚。
- 事务内的扣库存、建订单、建明细、清购物车共享**同一连接和同一提交点**：要么都可见，要么都撤销。
- 事务不负责并发安全。它保证原子性，但两个线程同时读"库存 1"再各自减 1，仍可能扣成 0 以下；
  并发要靠条件 UPDATE 或锁。

## 2. 事务传播与回滚基础

- **传播行为**：默认 `REQUIRED`——若当前已有事务就加入，没有就新建。本项目的 Controller →
  OrderServiceImpl（无事务） → OrderTransactionService.commit（新建事务）就是典型路径。
- **自调用陷阱（本项目专门规避）**：如果 `createOrder()` 和 `@Transactional commit()` 在同一个类里，
  `this.commit()` 不经过代理，注解完全失效，事务不会开启。因此把事务方法拆到独立 Bean，
  通过注入的对象调用，确保走代理。
- **异常被吞掉 = 事务照常提交**：`try { insert(); } catch (Exception e) { log... }` 之后方法正常返回，
  Spring 会提交。需要回滚就必须重抛，或 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`。
- **`rollbackFor`**：受检异常默认不回滚，写 `rollbackFor = Exception.class` 是常见稳妥做法。
- **事务不宜过长**：不要在事务里调用外部 HTTP 支付、发邮件等耗时操作（AGENTS 4.4）。本阶段事务内
  只有本地数据库写操作，模拟支付留到阶段 7 且独立成事务。

## 3. 为什么订单金额必须后端计算

前端传来的 `price` / `totalAmount` / `payAmount` 都是不可信输入：
浏览器里的 JS、抓包工具、脚本都能随意改。如果服务端直接采用，攻击者可以用 ¥0.01 下单 ¥599 的商品。

本项目的三层防线：
1. `OrderCreateDTO` **只声明** `cartItemIds` / `items(productId, quantity)` / `addressId` / `remark`，
   根本没有价格字段；请求体即使夹带 `price: 0.01` 也会被 Jackson 忽略（测试第 8 项）。
2. 单价只从 `product` 表实时读取：`price = productMapper.selectBatchIds(...)` 的结果，
   小计 `price × quantity`、总额累加都在 Service 里用 `BigDecimal` 计算。
3. 金额随订单主表与明细一起作为快照写入数据库；支付阶段也只能读订单表金额，不读前端金额。

## 4. 为什么订单需要商品与地址快照

商品会改名、改价、下架，用户地址会修改或删除。如果订单详情实时 JOIN 当前商品/地址表，历史订单
展示的就不是用户当时买的东西和收货地址，甚至会因商品删除而查不出明细——这既是功能错误，也是
财务与售后纠纷隐患。

因此下单瞬间把以下字段固化到 `orders` / `order_item`：
- 商品：名称、主图、单价、数量、小计（写进 `order_item`）；
- 地址：收货人、电话、省、市、区、详细地址（写进 `orders`）。

`OrderDetailVO` 只读这两张表，不查 `product` / `user_address`（测试"快照不随商品改名改变"）。
代价是数据冗余，换来的是历史可追溯与查询简单——这是订单系统的标准取舍。

## 5. 条件 UPDATE 如何避免并发超卖

错误做法："先 `SELECT stock`，Java 判断够，再 `UPDATE stock = stock - n`"。两个线程可能同时读到
库存 1，都判断通过，然后都扣 1，库存变成 -1（超卖）。

正确做法（AGENTS 6.5，本项目 `ProductMapper.xml`）：

```sql
UPDATE product
SET stock = stock - #{quantity}, sales = sales + #{quantity}, updated_at = NOW()
WHERE id = #{productId}
  AND stock >= #{quantity}
  AND status = 'ON_SALE'
  AND audit_status = 'APPROVED'
  AND deleted = 0
```

- 所有条件在数据库层、在 InnoDB 行锁保护下判断，两个并发更新会串行执行；
- 只有第一个满足 `stock >= n` 的更新成功（受影响行数=1），第二个因条件不满足影响 0 行；
- Service 检查 `rows != 1` 就抛 `BusinessException`，触发整个订单事务回滚；
- 状态/审核/删除条件同时写在 WHERE 里，防止"校验通过后商品被下架"的竞态（TOCTOU）。

## 6. 事务中每一步失败时数据库会发生什么

| 失败点 | 数据库状态 |
| --- | --- |
| 校验阶段（地址、商品、商家、数量） | 事务还没开启/尚无写入，数据库完全未变 |
| 条件扣库存返回 0 行 | 抛出异常；此前已扣的其他商品库存被回滚 |
| 订单主表 INSERT 失败 | 已扣库存回滚；无订单、无明细 |
| 订单明细 INSERT 失败 | 已扣库存、已插入的订单主表与前面几条明细全部回滚 |
| 清理购物车失败 | 上述全部回滚，购物车条目保留 |
| 全部成功、方法正常返回 | 事务提交：库存、订单、明细、购物车变更一次性对所有用户可见 |

三种中途失败场景在测试中通过故障注入点精确模拟，并直接查库验证（见 `STAGE6_TESTS.md` 第 3 节）。

## 7. 一个容易忽略的细节：购物车结算的来源校验

`cartItemIds` 模式下，查询购物车条目时必须同时带 `user_id = 当前用户`：

```java
cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
        .in(CartItem::getId, ids).eq(CartItem::getUserId, userId));
```

若查到的条数不等于请求的 ID 数，说明有人提交了别人的条目 ID 或不存在的 ID，直接返回 404。
只按 ID 查询会让恶意用户用他人购物车条目 ID 下单并删除他人数据——归属校验永远不能省。

## 8. 练习（不依赖 AI）

在现有项目上手动完成一次"库存不足回滚"验证：
1. 在数据库把某个商品库存改为 1，记下当前 `stock` 与 `sales`。
2. 用 `user_demo` 登录后，在结算页或 Postman 直接向 `POST /api/v1/orders` 提交该商品数量 5。
3. 预期返回 409，提示库存不足。回到数据库复查：`stock` 仍是 1、`sales` 没变、`orders` 没有新记录。
4. 再把数量改为 1 重新下单，预期成功，`stock` 变 0、`sales` 加 1、生成一条 PENDING_PAYMENT 订单。
5. 打开订单详情，确认地址与商品信息与下单时一致；随后把该商品改名，再刷新订单详情，确认名称不变。

思考题：如果第 3 步后发现库存少了 1 但订单不存在，说明哪里出了问题？（提示：条件 UPDATE 没有检查
受影响行数，或异常被 catch 吞掉导致事务提交。）
