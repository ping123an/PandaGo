# PandaGo 项目复盘

## 一、项目概览

PandaGo 是一个前后端分离的综合在线商城，同时作为 Java 初级后端的学习项目。
后端 Spring Boot 3 + Spring Security 6 + MyBatis-Plus + MySQL 8 + Redis 7，
前端 Vue 3 + TypeScript + Vite + Pinia + Element Plus。按 AGENTS 规范分 10 个阶段推进，
最终全量测试 118/118 通过，前端类型检查与构建通过，端到端 22/22 步演示通过。

## 二、真实难点与解决方案

### 难点 1：订单事务边界与"自调用陷阱"

**问题**：`@Transactional` 写在同类方法上，被同类另一方法直接 `this.xxx()` 调用时，
不经过 Spring 代理，事务注解失效，回滚不生效。

**方案**：把事务性写入拆到独立 Bean `OrderTransactionServiceImpl.commit()`；
Controller → `OrderServiceImpl.create`（只读校验，无事务）→ 注入的 transactional Bean。
跨 Bean 调用必经代理，事务必定生效。用 `FaultHook` 在扣库存后/主表/明细三处注入异常，
直接查库确认回滚。见 `STAGE6_LEARNING.md`、`Stage6OrderTests`。

### 难点 2：库存超卖

**问题**：先查库存再扣减的"检查-执行"分离在并发下会超卖。

**方案**：单条原子条件 UPDATE，`WHERE` 带 `stock>=qty + ON_SALE + APPROVED + deleted=0`，
受影响行数 !=1 即视为库存不足并回滚。库存=1 两人并发购买实测只一个成功，
stock 恰好归 0。购物车加购也曾出现"insert 再 update"死锁，改为单条
`INSERT ... ON DUPLICATE KEY UPDATE quantity=IF(quantity+n<=stock,...)`。

### 难点 3：支付/取消/超时的幂等

**问题**：重复点击、并发请求、定时任务重跑都可能导致重复扣款、重复回补库存。

**方案**三道防线叠加：
1. 唯一索引：`uk_payment_no`、`uk_payment_order`、`uk_review_order_item`；
2. 条件状态更新：UPDATE 带 `id + 归属 + 源状态`，只有 rows=1 才执行后续副作用（如回补库存）；
3. 状态机：`OrderStatusMachine.assertTransition` 拒绝非法操作，重复请求返回明确的 409。
重复支付带原流水号返回，重复取消不重复回补。

### 难点 4：JWT 无状态鉴权与越权防护

**问题**：既要无状态认证，又要防止用户访问他人资源、普通用户越权到管理端。

**方案**：JWT Filter 写 `SecurityContext`；URL 规则 + `@PreAuthorize` + Service 归属校验三重防护；
所有跨用户资源查询强制拼接当前用户/商家 ID，非本人统一返回 404 防探测；
DTO 不接受 `userId`/`merchantId`/价格等身份与金额字段。

### 难点 5：引入 Redis 而不改写已验收业务

**问题**：阶段 9 要求加缓存，但不能推翻阶段 6–8 已通过的订单/支付逻辑。

**方案**：只在读路径加 Cache Aside 旁路缓存，写操作删缓存失效，MySQL 仍是唯一事实来源；
Redis 异常全链路 try-catch 降级直查 MySQL；无 Redis 环境自动由 `NoOpCacheService` 接管。
超时关闭做成独立服务，复用阶段 7 的库存回补模型，不改其逻辑。

### 难点 6：统一异常处理的语义正确性

**问题**：阶段 10 演示时发现未映射路径返回 500 而非 404
（Spring Boot 3.2 抛 `NoResourceFoundException`，被兜底 Exception 吞掉）。

**方案**：在 `GlobalExceptionHandler` 增加专门的 `@ExceptionHandler` 映射为 404，
复测通过。这体现了"端到端验证"能发现单元测试覆盖不到的框架层细节。

### 难点 7：超时扫描的索引优化

**问题**：`WHERE status=? AND created_at<=? ORDER BY created_at,id` 无 status 前导索引，数据量大时全表扫描。

**方案**：新增覆盖索引 `(status, created_at, id)`（V6 迁移，幂等），EXPLAIN 从全表扫描变为 `Using index`。

## 三、技术取舍

- **不引入新架构**：阶段 10 明确不做"为格式统一而无差别重构"，只修 1 处真实缺陷 + 1 处索引优化。
- **模拟支付而非真实渠道**：支付是教学用 mock，无回调/对账/退款；文档与验收报告明确标注。
- **图片用 URL 白名单而非上传**：未接对象存储，禁止 `data:/file:/javascript:` 协议；无文件字节校验。
- **Knife4j 依赖保留但不启用**：接口文档用 Markdown（docs/API.md），避免为工具而工具。
- **无 Git 仓库**：项目目录未初始化 Git，无法提供提交历史与 diff。

## 四、测试与验证策略

- 事务回滚用 `FaultHook` 精确注入异常 + JdbcTemplate 直查数据库，而非仅看接口返回。
- 并发用 `CountDownLatch` 发令枪确保同时发起，验证"只一个成功"而非碰运气。
- 测试用专用 userId + 随机商品名，`@AfterEach` 彻底清理，终态检查全 0 残留，避免测试间污染。
- 端到端演示脚本 `docs/e2e_demo.py` 用真实 HTTP 走完整链路，可作为交付后可复现的验收工具。

## 五、个人复盘与可改进项

**收获**：
- 真正理解了"事务边界在哪、为什么独立 Bean"、"幂等为什么需要唯一索引 + 条件更新 + 状态机三层"。
- 学会了用端到端验证发现框架层问题（404 缺陷），意识到集成测试不能替代真实 HTTP 冒烟。
- 体会到"不改写已验收代码"的约束如何倒逼出旁路缓存、独立服务等低侵入设计。

**仍未实现 / 可改进**（详见 `FINAL_ACCEPTANCE.md`）：
- 真实支付渠道、回调、退款、对账；物流轨迹查询。
- 图片上传与对象存储、文件字节级校验。
- 在线接口文档（Knife4j/Swagger）、多分辨率真机截图、自动化 E2E（Playwright）。
- 本项目为教学项目，未做生产级可观测性（链路追踪、指标告警）与灰度/限流。

> 说明：以上均为项目真实情况，未实现项明确列出，不作为已交付能力。
