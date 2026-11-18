# 阶段 9 测试清单：Redis 缓存与超时订单定时任务

> 运行：`mvn -q clean package`（全量）或按类单独运行。
> 缓存测试用 Mockito 模拟 Redis，**不依赖真实 Redis 服务**；超时测试用真实 MySQL（3307）。
> 全量测试 **107/107 通过**（阶段 1-8 共 92 + 本阶段 15）。

## 一、缓存测试（10 项）

### Stage9CacheFallbackTests（5 项，Mock StringRedisTemplate）

| # | 用例 | 验证点 |
| --- | --- | --- |
| 1 | `cacheHitReturnsDeserializedValue` | **缓存命中**：预置 JSON，get 返回反序列化后的 VO（名称、BigDecimal 金额正确） |
| 2 | `cacheMissReturnsEmpty` | **缓存未命中**：Redis 返回 null → get 返回 empty，交给业务回源 |
| 3 | `nullValueCacheMarksMissingProduct` | **空值防穿透**：`__PANDAGO_NULL__` 标记被 isNull 识别，get 视为未命中 |
| 4 | `redisDownGetDegradesToMiss` | **Redis 不可用降级**：get 抛 `RedisConnectionFailureException` → 返回 empty，不把异常抛给业务 |
| 5 | `redisDownSetAndEvictDoNotThrow` | Redis 不可用时 set/setNull/evict/evictByPrefix 全部静默跳过，不抛异常 |

### Stage9CacheAsideTests（5 项，Mock CacheService + 真实 DB，类级事务回滚）

| # | 用例 | 验证点 |
| --- | --- | --- |
| 6 | `cacheHitReturnsCachedValueWithoutDatabase` | 业务命中缓存时直接返回缓存对象（用不存在的商品 ID，若不走缓存会抛 404） |
| 7 | `cacheMissReadsDatabaseAndWritesBack` | 未命中查库并 `set` 回填，TTL 为 DETAIL_TTL |
| 8 | `productUpdateEvictsDetailAndHotCache` | **商品更新后缓存失效**：evict 详情 + evictByPrefix 热门 |
| 9 | `productOffShelfEvictsDetailAndHotCache` | **商品下架后缓存失效**：同样清理两类缓存 |
| 10 | `categoryChangeEvictsCategoryListCache` | **分类修改后缓存失效**：evict `category:list` |

## 二、超时订单测试（5 项，Stage9TimeoutOrderTests，真实 MySQL）

| # | 用例 | 对应用户要求 | 验证点 |
| --- | --- | --- | --- |
| 11 | `timedOutOrderIsClosedAndStockRestored` | 超时订单正常关闭 | 2 小时前订单被关闭，状态 CANCELLED，库存回补一次 |
| 12 | `notTimedOutOrderStaysPending` | 未超时订单不关闭 | 5 分钟前订单仍 PENDING_PAYMENT，库存不回补 |
| 13 | `paidOrderIsNotClosed` | 已支付订单不关闭 | 已支付订单即使创建很久也不被关，库存不回补 |
| 14 | `repeatedCloseRestoresStockOnlyOnce` | 定时任务重复执行不重复恢复库存 | 连续 3 次关闭，仅首次成功，库存只回补一次 |
| 15 | `restockFailureRollsBackStatusAndStockTogether` | 超时取消事务失败时库存与状态一致 | 故障注入让回补失败 → 状态回滚到 PENDING_PAYMENT，库存保持扣减值 |

## 三、测试设计说明

- **为何缓存测试不用真实 Redis**：本机/测试环境没有 Redis 服务，且单元测试不应依赖外部中间件。
  用 Mockito 精确控制"命中/未命中/异常"三种输入，比起一个真实 Redis 更稳定、更快、可重复。
  这同时证明了一个重要事实：**业务正确性不依赖缓存是否可用**。
- **为何超时测试用真实 MySQL**：条件 UPDATE 的行锁语义、事务回滚、库存增量都是在真实数据库里发生的，
  必须用真实连接验证；Mock 无法反映 `WHERE status=? AND created_at<=?` 的实际影响行数。
- **数据隔离**：超时测试每例用独立订单号 + `S9-TIMEOUT-%` 商品，`@AfterEach` 全量清理；
  Cache Aside 测试用类级 `@Transactional` 自动回滚。终检确认测试后订单/明细/商品残留均为 0。

## 四、联调验证记录

- 迁移：本阶段无新增表/字段（`V3` 订单表已含 `created_at`，阶段 6 已建），无需新迁移脚本。
- 后端：`mvn clean package` 生成 jar，全量 107/107 通过。
- 启动：用新 jar 在 8080 重启（未启用缓存，`pandago.cache.enabled=false`），健康检查 200。
- 降级联调：无 Redis 环境下 `GET /products/hot`、`/categories`、`/products/1` 均返回 200，正常回源 MySQL。
- 前端：5173 服务正常，本阶段无前端改动。
- 数据终检：阶段 9 测试订单、明细、商品残留均为 0。

## 五、连接验证方式（有 Redis 时）

启用缓存：`PANDAGO_CACHE_ENABLED=true` + 配置 `PANDAGO_REDIS_HOST/PORT/PASSWORD`（密码走环境变量，不入库）。

```bash
# 1. 检查 Redis 是否已启动
redis-cli -h 127.0.0.1 -p 6379 ping          # 返回 PONG

# 2. 有密码时
redis-cli -h 127.0.0.1 -p 6379 -a "$PANDAGO_REDIS_PASSWORD" ping

# 3. 看 PandaGo 写入的缓存 key（带 pandago: 前缀）
redis-cli -h 127.0.0.1 -p 6379 KEYS 'pandago:*'

# 4. 看某个 key 的剩余过期时间（验证 TTL 有限、不永久）
redis-cli -h 127.0.0.1 -p 6379 TTL 'pandago:category:list'

# 5. 通过应用接口间接验证：第二次请求走缓存，删除 key 后第一次请求变慢/回源
curl http://127.0.0.1:8080/api/v1/products/hot
```

若 Redis 未启动或密码错误，应用仍正常启动，读接口自动降级直查 MySQL——这正是降级策略的预期表现。
