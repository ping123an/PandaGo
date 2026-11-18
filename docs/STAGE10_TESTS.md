# 阶段 10 测试结果与质量验证记录

本文档记录阶段 10 的测试执行、代码质量检查、端到端演示、并发与数据一致性验证的真实结果。

## 一、后端测试

### 1. 全量测试

```
mvn clean test                       # 118/118 通过，BUILD SUCCESS
mvn package -DskipTests              # 生成 pandago-backend-0.0.1-SNAPSHOT.jar
```

各测试类分布：

| 测试类 | 数量 | 覆盖 |
| --- | --- | --- |
| AuthIntegrationTests | 12 | 注册/登录/JWT/权限/无密码泄露/禁止提权 |
| CatalogIntegrationTests | 8 | 分类/商品列表/详情/排序白名单 |
| PandaGoApplicationTests | 2 | 上下文加载/健康检查 |
| Stage4ProductFlowTests | 14 | 商品管理/搜索/审核/图片 URL 校验 |
| Stage5UserCenterTests | 13 | 购物车/收藏/地址/并发加购 |
| Stage6OrderTests | 16 | 下单/快照/三类事务回滚/越权 |
| Stage7OrderStatusTests | 12 | 状态机/支付幂等/并发支付/取消回补/并发取消 |
| Stage8FulfillmentTests | 15 | 发货/确认/评价/管理端/完整链路 |
| Stage9CacheAsideTests | 5 | 缓存命中/回填/失效 |
| Stage9CacheFallbackTests | 5 | 缓存降级/空值 |
| Stage9TimeoutOrderTests | 5 | 超时关闭/未超时/已支付/重复执行/回滚 |
| **Stage10UnitTests（新增）** | **5** | 状态机操作/流转矩阵/非法操作/非法流转/非法状态 |
| **Stage10QualityIntegrationTests（新增）** | **6** | 统一异常/参数校验/权限/库存=1 并发购买/端到端一致性 |
| **合计** | **118** | |

### 2. 阶段 10 新增测试要点

- **核心 Service 单元测试**：`Stage10UnitTests` 不加载 Spring 上下文，直接对状态机这个"全项目唯一订单规则入口"做纯逻辑断言：五态操作矩阵、合法流转、非法操作抛 409、非法状态抛 500、描述回退。
- **统一异常处理**：参数校验失败→HTTP 400 + `code=400`；业务资源不存在→HTTP 404 + `code=404`；未知路径→404（见下方缺陷修复）。
- **权限控制**：普通用户访问管理端/商家端→403；未登录访问订单→401。
- **端到端一致性**：下单扣库存与销量→支付写流水且状态转待发货→发货→确认转已完成并写 `received_at`→评价→公开评价列表可见；全程库存/销量不再变化。

### 3. 测试中发现并修复的真实缺陷

`unknownEndpointReturns404` 首轮失败（期望 404 实得 500）：Spring Boot 3.2 对未映射静态资源抛
`NoResourceFoundException`，被全局兜底 `Exception` 处理器转成 500。
已在 `GlobalExceptionHandler` 增加专门的 `@ExceptionHandler(NoResourceFoundException.class)`
映射为 404，复测通过。这是对"验证统一异常处理"要求的实质产出。

## 二、前端检查

```
npm run type-check     # vue-tsc --noEmit，通过
npm run build          # 类型检查 + vite build，通过（1785 模块）
```

- 仅有 Vite "chunk > 500kB" 的**提示**（Element Plus 打包体积），非错误；本项目不做无谓的代码拆分重构。
- 路由、登录/退出、购物车→订单、商家发货、管理员审核等页面在阶段 1–9 已逐项经无头浏览器与 HTTP 联调验证。
- 错误/加载/空数据三态在各列表页齐全。
- 桌面与移动视口：Element Plus 响应式栅格 + 自定义 SCSS；未做真机多分辨率截图（见"未验证项"）。

## 三、端到端业务演示（`docs/e2e_demo.py`）

对运行中的 prod profile 后端实测 **22/22 步通过**，覆盖用户要求的全部 14 个环节：

| 步骤 | 结果 |
| --- | --- |
| 商家创建商品 / 提交审核 / 管理员审核通过 / 商家上架 | PASS |
| 注册普通用户 / 用户登录 / 管理员与商家登录 | PASS |
| 浏览分类（6）/ 商品列表 / 搜索命中 / 商品详情 | PASS |
| 添加购物车 / 添加收货地址 / 创建订单（金额后端计算） | PASS |
| 模拟支付（生成流水号）/ 商家查看订单 / 商家发货并填物流 | PASS |
| 用户确认收货 / 用户评价商品 | PASS |
| 越权验证：普通用户访问管理端 403、商家端 403、未登录访问订单 401 | PASS |

演示结束已清理全部 E2E 数据（用户/商品/订单/支付/评价/地址/购物车均 0 残留）。

## 四、并发与数据一致性测试

按用户要求记录输入条件、并发方式、实际结果、数据库终态：

| # | 场景 | 输入条件 | 并发方式 | 实际结果 | 数据库终态 |
| --- | --- | --- | --- | --- | --- |
| 1 | 库存=1 两人同时购买 | 商品 stock=1，两用户各买 1 件，均有有效地址 | 2 线程 CountDownLatch 同时发起 `POST /orders` | 1 个 200、1 个 409（库存不足） | stock=0、sales=1、订单数=1 |
| 2 | 重复提交支付 | 同一待支付订单连续/并发支付 | 2 线程同时 `POST /payments/mock` | 1 个 200、1 个 409 | 支付记录=1，状态待发货 |
| 3 | 并发取消同一订单 | 同一待支付订单，两线程同时取消 | 2 线程 `POST /orders/{id}/cancel` | 1 个 200、1 个 409 | 库存只回补一次，状态已取消 |
| 4 | 超时任务重复处理 | 构造超时未支付订单，重复执行关闭 | 串行重复调用关闭服务 | 首次关闭成功，重复 0 行 | 库存只回补一次，状态已取消 |

- 场景 2/3/4 由阶段 7/9 测试覆盖（`concurrentPaymentsOnlyOneSucceeds`、`concurrentCancelsOnlyOneSucceeds`、
  `repeatedCloseRestoresStockOnce`）；场景 1 由阶段 10 新增测试覆盖。
- 一致性终检：全部测试与演示结束后，`orders` / `payment_record` / `review` / `shipment` /
  测试商品 / E2E 用户均为 **0**。

## 五、代码质量检查结论

| 检查项 | 结论 |
| --- | --- |
| 后端分层 | 合理：controller / service(+impl) / mapper / entity / dto / vo / support 分层清晰 |
| Controller 业务逻辑 | 无；最长 108 行且多为注释，只做参数绑定、鉴权注解、Service 调用 |
| Service 重复代码 | 无有害重复；共用的库存可用性判定已抽 `ProductAvailabilityResolver` |
| DTO/VO/Entity 职责 | 清晰；DTO 不含 price/userId/merchantId，VO 不含 passwordHash |
| 异常处理与参数校验 | 完善（见上文缺陷修复）；`@Valid` + 枚举 Pattern + 状态机三重校验 |
| 日志敏感信息 | 无密码/Token/密钥打印；未知异常堆栈只写服务端 |
| SQL 效率 | 分页有 LIMIT、条件扣减带足条件；补覆盖索引消除超时扫描全表风险 |
| 配置密钥 | 全环境变量注入；prod JWT secret 无兜底值，缺失即启动失败 |
| 前端无用组件/重复请求 | 未发现无用组件；`getOrderStatus` 为预留 API 未被页面调用（保留） |
| 是否无差别重构 | 否；仅修复 1 处真实缺陷 + 1 处索引优化 + 1 个必要文档对齐 |
