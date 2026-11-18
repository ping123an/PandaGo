# PandaGo 开发涉及的 Java / Spring 知识点

按主题梳理项目中真实用到的技术点，每条都指向真实代码位置，便于回看与讲解。

## 1. Java 基础与并发

- **`BigDecimal` 金额计算**：订单金额一律用 `BigDecimal`，禁止 `double`（精度丢失）。见 `OrderServiceImpl`、`OrderTransactionServiceImpl`。
- **枚举承载状态与操作**：`OrderStatus`、`OrderOperation`、`AuditStatus`、`ProductStatus`、`RoleCode`，状态字符串与描述集中定义，`OrderStatus.describe()` 对非法值回退原串。
- **`EnumSet` / `Map.of` 不可变集合**：`OrderStatusMachine` 用 `EnumSet` 表达状态允许的操作，`Collections.unmodifiableSet` 对外暴露只读视图。
- **并发原语**：测试用 `CountDownLatch`（就绪门闩 + 发令枪）确保两个线程真正同时发起请求，验证防超卖/幂等。见各 `Stage*Tests` 的 `runConcurrently`。
- **记录类 `record`**：测试 fixture 用 `record OrderFixture(...)` 简洁承载一组返回值。
- **Lambda / Stream**：异常信息拼接、列表映射大量使用 Stream；`Collectors.joining` 拼接校验错误。

## 2. Spring Boot 核心

- **配置占位符与 profile**：`application.yml` + `application-{dev,test,prod}.yml`；`${VAR:default}` 环境变量注入，`@ActiveProfiles("test")` 切换测试配置。prod 对 JWT secret 不设默认值。
- **依赖注入**：构造器注入（`final` 字段 + `@RequiredArgsConstructor` / 显式构造器），无字段注入。
- **`@ConfigurationProperties` / `@Value`**：`ImageProperties` 绑定图片白名单，`JwtTokenProvider` 用 `@Value` 读取密钥。
- **Spring MVC 校验**：`@Valid` + Jakarta Validation（`@NotBlank`/`@Min`/`@Max`/`@Pattern`/`@Size`）；`@Validated` 触发方法参数级 `@Min/@Max`；分组顺序与错误信息拼接见 `GlobalExceptionHandler`。
- **统一响应与异常**：`Result<T>` / `PageResult<T>` 统一结构；`@RestControllerAdvice` 集中处理，业务异常码映射到 HTTP 状态（400/401/403/404/409/500）；未知异常不泄露堆栈。
- **Actuator**：仅暴露 `health,info`，`show-details: never`。

## 3. Spring Security 6 + JWT

- **无状态认证链**：关闭 session/csrf/formLogin/httpBasic，`SessionCreationPolicy.STATELESS`；`JwtAuthenticationFilter` 在 `UsernamePasswordAuthenticationFilter` 之前解析 Bearer Token 写入 `SecurityContext`。
- **JWT 签发与校验**：jjwt 0.12.5，`Keys.hmacShaKeyFor` 按密钥长度选 HS256/384；claims 仅放 uid/username/roles，不含密码；验签同时校验签名与过期时间。
- **三重鉴权**：URL 规则（`hasRole`）+ 方法级 `@PreAuthorize` + Service 内 `SecurityUtils.requireRole/requireCurrentUser`。资源归属在 Service 按 `user_id`/`merchant_id` 强制拼接查询条件，DTO 不接受身份字段。
- **异常处理分工**：URL 级失败走 `RestAuthenticationEntryPoint`(401)/`RestAccessDeniedHandler`(403)；方法级 `@PreAuthorize` 抛的 `AccessDeniedException` 在 MVC 内，由 `GlobalExceptionHandler` 兜底为 403。
- **BCrypt**：注册 `passwordEncoder.encode`，登录 `matches`；数据库只存 hash，任何 VO 都不返回密码字段。

## 4. MyBatis-Plus

- **BaseMapper + XML**：通用 CRUD 走 BaseMapper，复杂条件分页与关联查询写在 `mapper/*.xml`（`ProductMapper.xml`、`OrderMapper.xml` 等）。
- **分页插件**：`MybatisPlusInterceptor` + `PaginationInnerInterceptor`，`PageResult` 为纯 POJO（不直接暴露 `IPage`）。
- **条件构造器**：`LambdaQueryWrapper` 类型安全拼条件；逻辑删除全局配置 `deleted` 字段。
- **动态 SQL 与排序白名单**：`ProductSortResolver` 把 sort/order 映射为固定字段，非法值直接 400，杜绝 SQL 注入。
- **条件更新防并发**：`deductStock` 把 `stock>=qty`、`status`、`audit_status`、`deleted=0` 全放进 `WHERE`，用受影响行数判断是否成功；状态流转同样带 `id+归属+源状态` 条件。见 `ProductMapper.deductStock`、`OrderMapper.shipOrder/confirmReceipt/closeTimeoutOrder`。
- **批量查避免 N+1**：购物车 VO 先批量查商品再 Map 关联，不在循环里查库。
- **覆盖索引**：阶段 10 为超时扫描加 `(status, created_at, id)` 索引，EXPLAIN 从全表扫描变为 `Using index`。

## 5. 事务与一致性

- **独立 Bean 规避自调用陷阱**：`@Transactional` 放在 `OrderTransactionServiceImpl.commit()`，Controller→Service（只读校验）→跨 Bean 调用事务服务，确保代理生效。同类 `this.xxx()` 不经过代理。
- **`@Transactional(rollbackFor = Exception.class)`**：覆盖受检异常也要回滚；事务边界内顺序：条件扣库存 → 主表 → 明细快照 → 清购物车。
- **同事务多写**：支付（写支付记录 + 条件改订单状态）、取消（条件改状态 + 回补库存）、发货、确认收货、审核均在同一事务内，任一步失败整体回滚。
- **测试接缝 `FaultHook`**：`OrderFaultHook` / `PaymentFaultHook` / `OrderTimeoutFaultHook` 默认空实现，测试用 `@MockBean` 在指定步骤抛异常，直接 JdbcTemplate 查库验证回滚。

## 6. Redis 缓存

- **Cache Aside**：读缓存→未命中回源 MySQL→回填；写后删缓存；MySQL 是唯一事实来源，Redis 只是旁路加速。
- **降级**：`RedisCacheService` 所有 Redis 操作 try-catch，异常时静默返回未命中直查 MySQL；`enabled=false` 时由 `NoOpCacheService` 接管。
- **穿透/空值**：查不到的商品写 `__PANDAGO_NULL__` 短 TTL（30s）防穿透；TTL 分级（商品/分类 10min、热门 5min）。
- **序列化**：`StringRedisTemplate` + Jackson JSON，key 前缀 `pandago:`，统一在 `CacheKeys` 生成。

## 7. 定时任务

- **`@EnableScheduling` + `@Scheduled(fixedDelay=...)`**：`OrderTimeoutScheduler` 分批扫描超时未支付订单。
- **分页扫描 + 逐单事务**：先分批查 ID，再逐单在独立事务里条件关闭 + 回补库存，避免长事务；重复扫描幂等（条件 UPDATE 第二次 0 行）。
- **可关闭**：`schedule-enabled` 配置让测试环境不启动后台扫描，改由测试手动调用服务验证。

## 8. 前端要点

- **Axios 拦截器**：请求拦截注入 `Bearer` Token；响应拦截统一处理 401（清 Token 跳登录并带 `redirect`），登录页自身 401 跳过避免死循环。
- **Pinia + Vue Router 守卫**：user store 管理登录态/角色；路由守卫按登录态与角色放行，刷新时 `fetchProfile` 重建身份。
- **三态 UI**：列表页统一加载/空数据/错误三态；按钮 `payingIds`/`paying` 防重复点击。

## 练习（不依赖 AI，动手验证）

1. 在 `OrderStatusMachine` 新增一条非法流转断言，运行 `Stage10UnitTests` 看它如何被拒绝。
2. 把 `deductStock` 的 `stock>=qty` 条件去掉，重跑库存=1 并发测试，观察是否超卖（验证条件 UPDATE 的作用）。
3. 将 `pandago.cache.enabled=true` 并启动本地 Redis，观察 `Stage9CacheFallbackTests` 与真实 Redis 行为差异。
4. 给 `GlobalExceptionHandler` 再加一个自定义业务异常码，验证 HTTP 状态映射是否需要同步扩展。
