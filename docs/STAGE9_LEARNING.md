# 阶段 9 教学重点：Redis 缓存、Cache Aside 与超时订单定时任务

> 本文对应 `cache` 模块、`order` 模块的超时关闭服务与 `OrderTimeoutScheduler`。
> 测试与接口见 `STAGE9_TESTS.md`、`API.md`、`DATABASE.md`。
> 本阶段**没有改写阶段 6 的订单创建、阶段 7 的支付/取消、阶段 8 的发货/收货逻辑**：
> 缓存是加在读路径上的旁路增强，超时关闭是复用了阶段 7「条件状态更新 + 同事务库存回补」模型的新服务。

## 1. Redis 与 MySQL 的职责区别

| 维度 | MySQL（核心存储） | Redis（缓存） |
| --- | --- | --- |
| 角色 | 唯一事实来源（Single Source of Truth） | 加速读的旁路副本，可随时丢弃 |
| 数据 | 订单、支付、库存、用户、商品全量 | 读多写少的商品详情/分类/热门快照 |
| 一致性 | 强一致，事务保证 | 最终一致，允许短暂旧值 |
| 宕机影响 | 业务不可用 | 自动降级直查 MySQL，业务仍可用 |
| 正确性 | 库存扣减由条件 UPDATE 保证 | **绝不**用于判断库存是否足够 |

一句话原则：**缓存挂了，商城还能下单、支付、扣库存，只是读得慢一点**。
如果一家商城 Redis 宕机就下不了单，说明把缓存当成了核心存储，这是本阶段明确禁止的（AGENTS 1.1 / PRD 5.5）。

代码里如何体现？`CacheService` 抽象把 Redis 隔离在实现内部。业务层只调用 `get/set/evict`，
`RedisCacheService` 把所有 Redis 异常都吞掉并降级；换成 `NoOpCacheService`（`pandago.cache.enabled=false`）
则每次都未命中、每次回源数据库。`Stage9CacheFallbackTests.redisDownGetDegradesToMiss`
用 Mockito 让 Redis 抛 `RedisConnectionFailureException`，验证 `get` 返回 empty 而不是把异常抛给业务。

## 2. Cache Aside（旁路缓存）标准套路

读流程（`ProductServiceImpl.detail` / `hot`、`CategoryServiceImpl.listEnabledGrouped`）：

```
1. 读缓存  →  命中直接返回
2. 未命中  →  查 MySQL
3. 查到    →  写缓存（带 TTL）后返回
4. 查不到  →  写短 TTL 空值标记（防穿透）后返回 404
```

写流程（商品/分类的所有写方法）：

```
1. 更新 MySQL（事务提交）
2. 删除相关缓存（不是更新缓存）
```

**为什么写后是"删缓存"而不是"更新缓存"？** 两个原因：
- 并发写场景下，线程 A 先写库、线程 B 后写库，但 B 可能先把旧值写进缓存、A 后写新值，
  导致缓存里是旧值且长期不失效；删除缓存则下一次读必然回源拿最新值。
- 写操作可能最终失败回滚，若提前更新了缓存就会留下"库里没有、缓存里有"的脏数据。

本阶段写操作的失效点：
- 商品 update / updateStatus / audit（管理端与商家端）→ 删 `product:detail:{id}` + 清 `product:hot:*`
- 分类 create / update / updateStatus → 删 `category:list`

TTL 全部有限（AGENTS 6.7，杜绝永久缓存）：详情/分类 10 分钟、热门 5 分钟、空值 30 秒。
Key 统一带前缀 `pandago:`（`pandago.cache.key-prefix`），便于一个 Redis 实例上区分环境与项目。

## 3. 缓存穿透、击穿、雪崩

**穿透（查不存在的数据）**：请求一个根本不存在的商品 ID，缓存里永远没有，每次都打到 MySQL。
本阶段对策：`detail` 查不到时写 `setNull(key, 30s)` 空值标记，`isNull` 命中直接返回 404，
30 秒后自动过期，既挡住刷接口又不至于长期掩盖新建商品。

**击穿（热点 key 过期瞬间的并发洪峰）**：一个超热点商品缓存刚过期，成百上千请求同时回源 MySQL。
本项目是中等规模教学商城，**不引入复杂的分布式锁**（AGENTS 1.2 明确排除秒杀级设计）。
理解概念即可：生产方案通常是互斥锁重建，或热点 key 逻辑过期 + 后台异步刷新。

**雪崩（大面积 key 同时过期）**：若所有缓存 TTL 相同，某一时刻集体失效，数据库瞬间被打满。
本阶段对策：不同业务用不同 TTL（10min / 5min / 30s），天然错峰；配合降级策略，
即使真发生雪崩也只是"变慢"而非"宕机"。

## 4. Spring Scheduling 与定时任务幂等

`@EnableScheduling` 开启调度，`@Scheduled(fixedDelayString=...)` 让 `OrderTimeoutScheduler`
每轮结束后再等待固定间隔，避免任务执行时间波动导致堆叠。周期、超时时间、批量大小全部走配置：

| 配置 | 默认 | 含义 |
| --- | --- | --- |
| `pandago.order.timeout.minutes` | 30 | created_at 早于 now-N 分钟仍未支付 → 超时 |
| `pandago.order.timeout.batch-size` | 100 | 每批最多关闭多少单 |
| `pandago.order.timeout.fixed-delay-ms` | 60000 | 扫描周期 |
| `pandago.order.timeout.schedule-enabled` | true | 测试环境设 false 手动调用验证 |

**幂等的实现不是靠"记得扫过了"，而是靠数据库条件更新**：

```sql
UPDATE orders SET status = 'CANCELLED', updated_at = NOW()
WHERE id = ? AND status = 'PENDING_PAYMENT' AND created_at <= ?
```

- 已支付订单状态变了 → 影响 0 行
- 未超时订单 created_at 晚于 cutoff → 影响 0 行
- 定时任务重复扫同一单 → 第一次已改成 CANCELLED，第二次影响 0 行

只有影响行数为 1（抢到变更权）的请求才回补库存，所以重复执行绝不会重复回补。
这与阶段 7 用户取消、阶段 9 秒杀式并发控制是同一个「条件 UPDATE 抢锁」模型，没有引入新范式。

## 5. 事务一致性：状态与库存同生共死

`OrderTimeoutCloseServiceImpl.closeOne` 标 `@Transactional(rollbackFor = Exception.class)`，
把「订单状态改 CANCELLED」和「逐项回补库存」放在同一事务：

```
条件 UPDATE 状态（rows==1 才继续）
   ↓
for each 明细: restoreStock
   ↓  任一回补失败 → 抛异常 → 整个事务回滚 → 状态回到 PENDING_PAYMENT
完成
```

`Stage9TimeoutOrderTests.restockFailureRollsBackStatusAndStockTogether` 用故障注入点
`OrderTimeoutFaultHook` 模拟「状态已改但回补失败」，验证订单状态回到 PENDING_PAYMENT、
库存仍是扣减后的值，两者始终一致。`repeatedCloseRestoresStockOnlyOnce` 验证重复执行库存只回补一次。

**逐单独立事务**：扫描服务循环调用 `closeOne`，每单一个事务。单笔失败只 WARN 并跳过，
不阻断整批，也不会因为一个坏订单回滚掉前面已经成功关闭的所有订单。

## 6. 日志策略：不刷屏

定时任务默认每 60 秒跑一次。若每次都打印"开始扫描/无订单/结束"，一天就是 thousands 条无用日志。
本阶段：无超时订单时只 DEBUG（默认不输出）；只有真正关闭了订单才输出一条 INFO 汇总
（命中 N 单 / 成功 M 单 / cutoff）；单笔失败输出 WARN。这样既能追溯，又不淹没日志。

## 7. 缓存不是唯一来源：三种一致性保障叠加

1. **降级**：Redis 不可用 → 等价于未命中 → 回源 MySQL（读路径兜底）。
2. **失效**：写操作删缓存，保证下次读拿到最新值（写路径兜底）。
3. **TTL**：即使漏了失效，最多旧值存活 10 分钟自动消失（时间兜底）。

三层叠加，任何一层单独失效都不会让系统返回错误的核心数据。这就是「缓存只做加速，
真实数据永远在 MySQL」的工程含义。

## 练习（自检）

1. 把 `pandago.cache.enabled` 改成 true 并启动一个本地 Redis，观察第二次请求 `GET /products/hot`
   是否命中（可用 `redis-cli KEYS 'pandago:*'` 看 key）。
2. 关闭 Redis 再请求同样的接口，确认仍返回 200——解释为什么。
3. 手动把某订单 `created_at` 改成 2 小时前、`status=PENDING_PAYMENT`，调用
   `POST` 触发一次扫描（或直接等方法），看它是否变 CANCELLED 且库存回补。
4. 思考：如果超时关闭只改状态、不回补库存会发生什么？如果先回补库存、状态更新失败又会发生什么？
   本阶段的事务边界如何同时避免这两种情况？
