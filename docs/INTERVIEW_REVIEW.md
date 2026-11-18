# PandaGo 面试复盘（基于真实代码）

> 使用说明：以下每个问题都基于本项目**真实存在**的代码与设计。回答前先到"代码位置"处打开对应文件确认，
> 用自己的话复述；不要背诵，也不要声称实现了本项目没有的功能（文末"红线"列出不能说的内容）。

---

## 1. 项目整体架构

**面试官可能怎么问**："介绍一下你的项目架构，为什么这样分层？"

**基于本项目代码的回答**：
PandaGo 是前后端分离的模块化单体商城。后端按业务域分包（`com.pandago.product/order/payment/...`），
每个模块内部分 `controller / service(+impl) / mapper / entity / dto / vo`；公共能力集中在 `common`
（统一响应 `Result`、异常、枚举、配置），横切逻辑在 `security`（JWT/Spring Security）、`cache`（缓存）。
Controller 只做参数绑定、鉴权注解和 Service 调用，不含业务逻辑；事务写在独立的 `*TransactionServiceImpl`。
前端 Vue 3 + TS，按 `api / stores / router / views / components / layouts` 分层，Pinia 管状态，Axios 拦截器统一带 Token 与处理 401。
技术栈：Spring Boot 3、Security 6、MyBatis-Plus、MySQL 8、Redis 7、Vue 3。

**可能的追问**："为什么不用微服务？" → 项目定位是教学与单体完整业务，模块边界已按业务域划清，
拆微服务会引入分布式事务、服务发现等复杂度，收益不抵成本；代码里用独立 Bean 拆事务边界，已能体现分层思想。

**代码位置**：`backend/src/main/java/com/pandago/` 目录结构、`frontend/src/` 目录、`README.md` 技术栈表。

---

## 2. 用户登录认证流程

**面试官可能怎么问**："用户从输入密码到后续请求，认证是怎么走的？"

**基于本项目代码的回答**：
注册：`POST /auth/register`，`AuthServiceImpl.register` 校验两次密码一致、BCrypt 编码后存 `user.password_hash`，
同事务建用户并分配 `USER` 角色（`user_role`），唯一索引 `uk_user_username` 防并发重名，捕获 `DuplicateKeyException` 返回 409。
登录：`POST /auth/login`，`passwordEncoder.matches` 校验，查角色列表，`JwtTokenProvider.generateToken`
签发只含 uid/username/roles 的 JWT，返回 `LoginVO`（无密码字段）。
后续请求：`JwtAuthenticationFilter` 从 `Authorization: Bearer` 解析并验签，写 `SecurityContext`；
`@PreAuthorize` 和 URL 规则据此鉴权；失效/过期 Token 由 `RestAuthenticationEntryPoint` 返回 401。

**可能的追问**："Token 放哪里？如何防 XSS/CSRF？" → 项目前端用 Token 字符串配合 Axios 头发送，
无 Cookie 会话，因此关闭 CSRF；XSS 依赖前端转义与不执行用户输入，JWT 不写入 localStorage 之外可被脚本读取的位置是更进一步的加固点（本项目教学实现未做 HttpOnly Cookie 方案）。

**代码位置**：`AuthServiceImpl.java`、`JwtTokenProvider.java`、`JwtAuthenticationFilter.java`、`SecurityConfig.java`、前端 `stores/user.ts`、`api/request.ts`。

---

## 3. Spring Security 与 JWT 的实现

**面试官可能怎么问**："Spring Security 的过滤器链你怎么配的？JWT 在哪一步校验？"

**基于本项目代码的回答**：
`SecurityConfig` 关闭 session/csrf/formLogin/httpBasic，`SessionCreationPolicy.STATELESS`；
认证入口与拒绝处理器分别返回统一结构的 401/403；URL 规则里公开注册/登录/健康检查/商品只读接口，
`/admin/**` 要 ADMIN、`/merchant/**` 要 MERCHANT 或 ADMIN，其余需登录；开启 `@EnableMethodSecurity` 支持 `@PreAuthorize`。
`JwtAuthenticationFilter` 插在 `UsernamePasswordAuthenticationFilter` 之前：有 Token 且未认证且 `validate` 通过，
则构造 `UsernamePasswordAuthenticationToken` 写入 `SecurityContext`。`JwtTokenProvider` 用 jjwt 0.12.5，
`Keys.hmacShaKeyFor` 按密钥长度自动选 HS256/384，claims 只放非敏感字段，验签同时校验过期时间。

**可能的追问**："方法级 @PreAuthorize 抛异常为什么有时是 500？" → 它发生在 MVC 内，会被
`@RestControllerAdvice` 的兜底 Exception 捕获成 500；项目专门加了 `@ExceptionHandler(AccessDeniedException)` 返回 403。

**代码位置**：`security/SecurityConfig.java`、`JwtTokenProvider.java`、`JwtAuthenticationFilter.java`、`common/exception/GlobalExceptionHandler.java`。

---

## 4. MyBatis-Plus 分页和动态查询

**面试官可能怎么问**："商品列表的分页、筛选、排序怎么实现？怎么防 SQL 注入？"

**基于本项目代码的回答**：
用 MyBatis-Plus 的 `PaginationInnerInterceptor` 分页插件，Controller 限制 `pageNum>=1`、`1<=pageSize<=50`
（`@Min/@Max`），Service 返回纯 POJO `PageResult`（不暴露 `IPage`）。
动态条件（关键词/分类/价格区间/状态）写在 `ProductMapper.xml`，只返回 `ON_SALE + APPROVED + deleted=0`。
排序用白名单：`ProductSortResolver` 把 `sort=price/sales/newest/default` 和 `order=asc/desc` 映射为固定列名与方向，
白名单外直接返回 400，不拼接用户输入。关键词也做了字符集限制。

**可能的追问**："分页插件原理？" → 拦截 Executor 的查询，基于方言自动追加 `LIMIT`，并发执行 count 与数据查询。

**代码位置**：`common/config/MyBatisPlusConfig.java`、`product/support/ProductSortResolver.java`、`resources/mapper/ProductMapper.xml`、`common/api/PageResult.java`。

---

## 5. 订单创建事务

**面试官可能怎么问**："下单流程是什么？事务边界在哪？为什么放那里？"

**基于本项目代码的回答**：
Controller → `OrderServiceImpl.create`（只读校验：地址归属、商品存在/上架/审核/同商家、数量与实时库存，
金额用 BigDecimal 后端算）→ 跨 Bean 调 `OrderTransactionServiceImpl.commit()`。
事务边界就在 commit 上：`@Transactional(rollbackFor=Exception.class)`，顺序为
条件扣库存（受影响行数校验）→ 订单主表（地址/金额快照）→ 订单明细（商品快照）→ 清购物车，任一步失败整体回滚。
拆独立 Bean 是为了避开 Spring AOP 自调用陷阱——同类 `this.xxx()` 不经过代理，事务会失效。
测试用 `OrderFaultHook` 在三处注入异常，JdbcTemplate 直查库确认全部回滚。

**可能的追问**："快照为什么重要？" → 商品改名/降价后历史订单不能变，所以下单时把名称/图片/单价/地址写进订单与明细快照。

**代码位置**：`order/service/impl/OrderServiceImpl.java`、`OrderTransactionServiceImpl.java`、`order/support/OrderDraft.java`、`Stage6OrderTests.java`。

---

## 6. 库存防超卖方案

**面试官可能怎么问**："库存为 1，两个人同时下单，怎么保证不超卖？"

**基于本项目代码的回答**：
核心是单条原子条件 UPDATE，数据库层面保证：
`UPDATE product SET stock=stock-?, sales=sales+? WHERE id=? AND stock>=? AND status='ON_SALE' AND audit_status='APPROVED' AND deleted=0`。
受影响行数必须为 1，否则抛 `BusinessException` 让整个下单事务回滚。
这避免了"先查库存再扣"的 check-then-act 竞态。购物车加购也遇到过"insert 失败再 update"的死锁，
改成单条 `INSERT ... ON DUPLICATE KEY UPDATE quantity=IF(quantity+n<=stock,...)`，0 行即超库存。
并发测试用 `CountDownLatch` 让两线程同时下单，结果恰好一个 200、一个 409，终态 stock=0、sales=1、订单=1。

**可能的追问**："为什么不用 synchronized / 分布式锁？" → 条件 UPDATE 由数据库行锁保证原子性，更简单可靠；
synchronized 限于单 JVM 实例，分布式锁需额外组件与维护成本。

**代码位置**：`product/mapper/ProductMapper.java` 与 `resources/mapper/ProductMapper.xml`（deductStock 条件 UPDATE）、`cart/mapper/CartItemMapper.java`（`INSERT ... ON DUPLICATE KEY UPDATE`）、`order/service/impl/OrderTransactionServiceImpl.java`、`Stage10QualityIntegrationTests.java`（库存=1 并发）、`Stage5UserCenterTests.java`（加购并发）。

---

## 7. 模拟支付幂等性

**面试官可能怎么问**："重复点击支付会怎样？怎么保证只扣一次/只生成一条流水？"

**基于本项目代码的回答**：
`POST /payments/mock` 只接收 `orderNo`，实付金额只取 `orders.total_amount`（前端夹带金额被忽略）。
`PaymentTransactionServiceImpl.pay` 在同一事务里写 `payment_record` + 条件 UPDATE 订单状态
（`id + user_id + 源状态`，状态机先 `assertTransition`）。
幂等靠三道防线：唯一索引 `uk_payment_no` 和 `uk_payment_order`（一单一支付）、条件状态更新（并发只有一个改动成功）、
状态机拒绝非法操作（已支付再支付返回 409 并带原流水号）。支付事务里也有 `PaymentFaultHook` 测试回滚。

**可能的追问**："为什么订单金额不从前端取？" → 防止篡改价格；DTO 根本不声明金额字段，Jackson 忽略多余字段。

**代码位置**：`payment/service/impl/PaymentTransactionServiceImpl.java`、`PaymentServiceImpl.java`、`payment/support/PaymentNoGenerator.java`、`V4__stage7_payment.sql`、`Stage7OrderStatusTests.java`。

---

## 8. 订单状态流转

**面试官可能怎么问**："订单有哪些状态？谁来控制流转？"

**基于本项目代码的回答**：
五态：`PENDING_PAYMENT / PENDING_SHIPMENT / PENDING_RECEIPT / COMPLETED / CANCELLED`。
`OrderStatusMachine` 是唯一规则入口，用 `Map<状态, 允许操作>` 和 `Map<状态, 允许目标状态>` 描述，
支付/取消/发货/确认/超时关闭都调用 `assertTransition`，Controller 从不直接 `setStatus`。
状态查询接口 `GET /orders/{id}/status` 返回后端算出的 `canPay/canCancel/canShip/canConfirm`，
前端按钮只是展示，真正的允许与否由后端判定。状态机只负责"应不应该"，并发安全由条件 UPDATE + 唯一索引保证。

**可能的追问**："如果用户重复确认收货？" → 状态机拒绝，返回 409；条件 UPDATE 带源状态，重复不生效。

**代码位置**：`order/support/OrderStatusMachine.java`、`OrderOperation.java`、`common/enums/OrderStatus.java`、`Stage10UnitTests.java`。

---

## 9. Redis 缓存策略

**面试官可能怎么问**："缓存怎么和数据库保持一致？Redis 挂了怎么办？"

**基于本项目代码的回答**：
用 Cache Aside：读缓存→未命中回源 MySQL→回填；写操作成功后删缓存（商品增删改/上下架/审核、分类增删改）。
MySQL 是唯一事实来源，缓存只是旁路加速。Key 集中在 `CacheKeys`（`pandago:product:detail:{id}`、
`pandago:category:list`、`pandago:product:hot:{limit}`），TTL 分级（详情/分类 10min、热门 5min、空值 30s 防穿透）。
`CacheService` 抽象出 `RedisCacheService`（StringRedisTemplate + Jackson JSON）与 `NoOpCacheService`；
Redis 所有操作 try-catch 降级，未启用或异常时静默直查 MySQL，业务无感。

**可能的追问**："缓存击穿/雪崩怎么防？" → 本项目用空值短 TTL 防穿透、TTL 分散与降级兜底降低雪崩影响；
未做互斥锁重建与热点永不过期，属于后续可优化点，面试要如实说明。

**代码位置**：`cache/CacheService.java`、`RedisCacheService.java`、`NoOpCacheService.java`、`CacheKeys.java`、`product/service/impl/ProductServiceImpl.java`。

---

## 10. 定时任务关闭超时订单

**面试官可能怎么问**："超时未支付订单怎么处理？重复执行会不会重复回补库存？"

**基于本项目代码的回答**：
`@EnableScheduling` + `OrderTimeoutScheduler` 的 `@Scheduled(fixedDelay=...)` 周期扫描。
`OrderTimeoutScanServiceImpl` 分批查 ID（LIMIT batch-size，避免长事务），逐单交给
`OrderTimeoutCloseServiceImpl`（独立事务）：`closeTimeoutOrder` 条件 UPDATE
（`id + status=PENDING_PAYMENT + created_at<=cutoff`），只有影响 1 行才回补库存（复用阶段 7 的 restoreStock）。
重复扫描时第二次条件 UPDATE 影响 0 行，不会重复回补——这就是幂等。`schedule-enabled` 可关闭，
测试环境关闭后台扫描，改由测试手动调用服务验证；`OrderTimeoutFaultHook` 验证事务回滚。

**可能的追问**："为什么逐单事务而不是一个大批次事务？" → 单条失败不影响其他订单，事务粒度小、锁持有时间短。

**代码位置**：`order/scheduler/OrderTimeoutScheduler.java`、`service/impl/OrderTimeoutScanServiceImpl.java`、`OrderTimeoutCloseServiceImpl.java`、`OrderMapper.xml`（closeTimeoutOrder / selectTimedOutOrderIds）、`Stage9TimeoutOrderTests.java`。

---

## 11. 关键索引和 SQL 优化

**面试官可能怎么问**："做了哪些索引优化？怎么验证有效？"

**基于本项目代码的回答**：
唯一索引保证业务约束：`uk_user_username`、`uk_payment_no`/`uk_payment_order`（支付幂等）、
`uk_review_order_item`（一单一评）、购物车/收藏 `(user_id, product_id)`、默认地址生成列唯一索引。
普通索引：`idx_orders_user`、`idx_orders_merchant`、`idx_order_item_order`。
阶段 10 为超时扫描补 `idx_orders_status_created(status, created_at, id)` 覆盖索引——
因为原有两个索引都不以 `status` 为最左列，扫描会全表。用 `EXPLAIN` 验证：
优化前无可用索引，优化后 `key=idx_orders_status_created`、`Extra=Using index`。
迁移脚本 V6 用 `information_schema` 判重，可重复执行。SQL 层面还有分页 LIMIT、
批量查避免 N+1、条件 UPDATE 带足条件、排序白名单防注入。

**可能的追问**："为什么索引顺序是 status, created_at, id？" → 匹配 WHERE 的等值列 status 在前、范围列 created_at 次之，
id 加入形成覆盖索引，让 ORDER BY 与取 id 都能走索引。

**代码位置**：`database/schema.sql`、`database/migration/V6__stage10_order_timeout_index.sql`、`docs/DATABASE.md` 第 12 节、`resources/mapper/OrderMapper.xml`。

---

## 12. 遇到的问题及解决过程

**面试官可能怎么问**："开发中遇到最难的问题是什么，怎么解决的？"

**基于本项目代码的回答**（挑两个讲，讲清"现象→定位→方案→验证"）：
1. **购物车并发加购死锁**：初版"insert 失败再 update"，8 线程下单时死锁。定位到是先插后更的两步竞态，
   改为单条 `INSERT ... ON DUPLICATE KEY UPDATE quantity=IF(quantity+n<=stock,...)`，受影响行数 0 即超库存，
   并发测试通过，`finally` 清理测试数据防污染。
2. **未知路径返回 500**：阶段 10 端到端演示发现访问不存在的接口返回 500 而非 404。
   定位到 Spring Boot 3.2 抛 `NoResourceFoundException`，被全局兜底 `Exception` 吞掉；
   在 `GlobalExceptionHandler` 加专门的 `@ExceptionHandler` 映射 404，复测通过。
   收获：集成测试覆盖不到框架层细节，端到端冒烟很有必要。

**可能的追问**："测试怎么保证事务真的回滚了？" → 不只看接口返回，而在 `FaultHook` 抛异常后直接用 JdbcTemplate 查库确认库存/订单/明细没落库。

**代码位置**：`cart/mapper/CartItemMapper.java`、`cart/service/impl/CartServiceImpl.java`、`common/exception/GlobalExceptionHandler.java`、`Stage5UserCenterTests.java`、`Stage10QualityIntegrationTests.java`。

---

## 红线：面试时不能声称实现的能力

以下均为本项目**未实现**，说了会被追问穿帮：
- 真实支付渠道、支付回调、退款、对账（本项目是模拟支付）。
- 真实物流轨迹查询（只有物流公司 + 单号）。
- 图片/文件上传与对象存储、真实文件字节校验（只有 URL 白名单）。
- 在线 API 文档 Knife4j/Swagger（pom 有依赖但未启用，文档是 Markdown）。
- 微服务/分布式事务/消息队列/限流熔断/链路追踪。
- 生产环境压测、灰度发布、真实多地域容灾。

详见 `docs/FINAL_ACCEPTANCE.md` 的"未实现功能"清单与 `docs/PROJECT_REVIEW.md`。
