# PandaGo 综合型在线商城

> PandaGo —— 一只靠谱的购物熊猫。现代简约电商风格，参考京东的信息层级与购物体验，使用原创熊猫主题视觉素材。

一个前后端分离、模块化单体的综合在线商城，同时作为 Java 初级后端学习项目。

**前端视觉基调**：原创熊猫主题（熊猫黑 `#1f2329` + 纸白 `#ffffff`）+ 电商红强调色 `#e1251b`，辅以竹青 `#16a34a`。整体沿用 PRD 要求的现代简约电商信息层级，不使用任何第三方品牌素材。

## 技术栈

| 端 | 技术 |
| --- | --- |
| 后端 | Java 17 · Spring Boot 3 · Spring Security 6 · JWT · MyBatis-Plus 3 · MySQL 8 · Redis 7 · Maven |
| 前端 | Vue 3 · TypeScript · Vite · Vue Router · Pinia · Axios · Element Plus · SCSS |
| 部署 | Docker · Docker Compose（MySQL / Redis / Spring Boot / Nginx） |
| 文档 | Markdown（docs/），接口说明见 `docs/API.md`（未接入 Knife4j/Swagger） |

## 目录结构

```
PandaGo/
├── backend/          # Spring Boot 后端（com.pandago）
│   └── src/main/java/com/pandago/
│       ├── common/   # 统一响应、异常、配置、枚举、常量、工具
│       ├── security/ # Spring Security + JWT
│       └── auth/ user/ category/ product/ cart/ address/ favorite/
│           order/ payment/ shipment/ review/ merchant/ admin/   # 业务模块
├── frontend/         # Vue 3 前端
│   └── src/
│       ├── api/        # 接口封装
│       ├── assets/     # 静态资源与全局样式
│       ├── components/ # 通用组件
│       ├── layouts/    # 布局（用户端 / 商家端 / 管理端）
│       ├── router/     # 路由与访问控制
│       ├── stores/     # Pinia 状态
│       ├── types/      # TypeScript 类型
│       ├── utils/      # 工具函数
│       └── views/      # 页面（user / merchant / admin）
├── database/         # schema.sql、init-data.sql、migration/（V1~V6）
├── docs/             # API、DATABASE、DEPLOYMENT、LEARNING_NOTES、PROJECT_REVIEW、各阶段测试与教学、e2e_demo.py
├── backend/Dockerfile # 后端多阶段构建镜像
├── frontend/Dockerfile + nginx.conf # 前端构建 + Nginx 托管与 /api 反代
├── docker-compose.yml # MySQL + Redis + backend + frontend 全栈编排
├── .env.example      # 部署环境变量样例（密码均为占位，必须修改）
├── AGENTS_4.md       # AI 开发规范（必读）
├── PRD_1.md          # 产品需求（待补充）
└── TECH_DESIGN_3.md  # 技术设计
```

## 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 20+ / npm 10+
- MySQL 8（阶段 2 起必需）
- Redis 7（阶段 9 起；本地不装也可自动降级直查 MySQL）
- Docker 24+ / Docker Compose v2（仅容器部署需要，非必需）

## 快速启动

### 1. 初始化数据库

```bash
mysql --default-character-set=utf8mb4 < database/schema.sql
mysql --default-character-set=utf8mb4 < database/init-data.sql
```

> 已有较旧数据库时，按顺序执行对应增量迁移（均可重复执行）：
> `mysql --default-character-set=utf8mb4 < database/migration/V1__stage4_product_audit.sql`
> `mysql --default-character-set=utf8mb4 < database/migration/V2__stage5_cart_favorite_address.sql`
> `mysql --default-character-set=utf8mb4 < database/migration/V3__stage6_orders.sql`
> `mysql --default-character-set=utf8mb4 < database/migration/V4__stage7_payment.sql`
> `mysql --default-character-set=utf8mb4 < database/migration/V5__stage8_shipment_review_admin.sql`
> `mysql --default-character-set=utf8mb4 < database/migration/V6__stage10_order_timeout_index.sql`（超时扫描优化索引）
> `mysql --default-character-set=utf8mb4 < database/migration/V7__stage_checkout_split.sql`（购物车多商家拆单）
> `mysql --default-character-set=utf8mb4 < database/migration/V8__stage_order_source.sql`（订单来源）
> `mysql --default-character-set=utf8mb4 < database/migration/V9__merchant_profile.sql`（商家资料）
> `mysql --default-character-set=utf8mb4 < database/migration/V10__admin_audit_demo.sql`（审核演示数据）

> 需要浏览器演示商家/管理员端时，再执行本地演示账号脚本（仅开发环境）：
> `mysql --default-character-set=utf8mb4 < database/demo-stage4-users.sql`
> 账号：`merchant_demo` / `user_demo` / `admin_demo`，初始密码均为 `Panda@12345`。
> 这些账号带已知密码，禁止在生产环境执行该脚本。

> 必须显式指定 utf8mb4 客户端字符集，否则 Windows 默认 GBK 会把中文写成乱码。
> 两个脚本都可重复执行：只 `CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE`，**不会清空或覆盖已有数据**。

### 2. 配置数据库凭据

凭据只从环境变量读取，不写入仓库文件：

| 变量 | 说明 | 示例 |
| --- | --- | --- |
| `PANDAGO_DB_URL` | JDBC 地址 | `jdbc:mysql://127.0.0.1:3306/pandago?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false` |
| `PANDAGO_DB_USER` | 数据库用户 | `pandago` |
| `PANDAGO_DB_PASSWORD` | 数据库密码 | 由本机环境提供 |
| `PANDAGO_TEST_DB_URL` / `_USER` / `_PASSWORD` | 测试库（可选，默认同开发库） | — |
| `PANDAGO_JWT_SECRET` | JWT 签名密钥，**>=32 字节，生产必须设置** | 示例：`pandago-integration-test-secret-key-2026-at-least-32bytes` |
| `PANDAGO_JWT_EXPIRATION_MS` | Token 有效期毫秒（可选，默认 86400000=24h） | `86400000` |

> 未设置 `PANDAGO_JWT_SECRET` 时使用 application.yml 中的本地开发兜底值，**仅可用于本地开发，禁止上生产**。

### 3. 后端

```bash
cd backend
mvn spring-boot:run
```

默认端口 `8080`，接口基础路径 `/api/v1`。启动后访问：

- 健康检查：<http://localhost:8080/api/v1/health>
- 分类列表：<http://localhost:8080/api/v1/categories>
- 商品分页：<http://localhost:8080/api/v1/products?pageNum=1&pageSize=12>
- 接口文档（后续阶段接入）：<http://localhost:8080/doc.html>

### 4. 前端

```bash
cd frontend
npm install
npm run dev
```

默认访问 <http://localhost:5173>。开发模式下 `/api` 请求会自动代理到后端 `8080`。

## 阶段 1 已交付内容

**后端**（`com.pandago`，Spring Boot 3.2.5）
- 启动类 `PandaGoApplication`、统一响应 `Result` / `PageResult`、错误码 `ResultCode`
- 业务异常 `BusinessException` 与 `@RestControllerAdvice` 全局异常处理
- 开发期 CORS 配置（仅允许 5173）、健康检查 `GET /api/v1/health`
- 上下文加载 + 健康检查 MockMvc 测试，`mvn test` 通过

**前端**（Vue 3 + TypeScript + Vite）
- 入口 `main.ts` / `App.vue`、Element Plus 与 Pinia 接入
- 三端路由骨架：用户端 12 个页面、商家端 4 个、管理端 6 个，未开发页面统一由 `PagePlaceholder` 占位
- 用户端 `UserLayout`（搜索、分类导航、页脚）、后台 `DashboardLayout`（侧边菜单、面包屑）
- PandaGo 主题：原创熊猫 SVG Logo/favicon、熊猫首页横幅、六大分类入口
- Axios 统一封装（`/api` + Token 拦截器）、Pinia 用户 store、API 类型定义
- 首页自动调用 `/api/v1/health`，可直观看到前后端是否连通

## 阶段 2 已交付内容

**数据库**：`pandago` 库 6 张表（user / role / user_role / category / product / product_image），utf8mb4 + InnoDB；
初始化 3 角色、13 分类、10 商品、10 图片。设计细节见 `docs/DATABASE.md`
（含 TECH_DESIGN 数据库章节缺失时的最小补充说明）。不写入任何用户密码。

**后端**：MyBatis-Plus + MySQL 驱动、分页插件、逻辑删除；6 个 Entity、6 个 Mapper（含商品条件分页 Mapper XML）、
分类/商品 DTO 与 VO；分类列表与增删改启停、商品分页/详情/新增/修改/上下架。
新增商品默认 `PENDING + OFF_SALE`，未审核商品不能上架。

**前端**：首页分类区域与热门商品改为真实接口；商品卡片组件、商品列表页（分类筛选、排序、关键词、分页）、
商品详情页（真实数据 + 多图切换）。无商品时显示空状态，加载中显示 loading。

## 阶段 3 已交付内容

**后端认证**：Spring Security 6 + jjwt 0.12.5（HS256/384）、无状态 Session、`BCryptPasswordEncoder`。
- 接口：`POST /auth/register`、`POST /auth/login`、`GET /auth/me`（均带 `/api/v1` 前缀）。
- 注册：参数校验 + 用户名唯一检查 + BCrypt 哈希 + `@Transactional` 分配 USER 角色 + 唯一索引防并发；**不接受任何角色提权参数**。
- 登录：校验账号密码与禁用状态，签发只含 id/username/roles 的 JWT，返回 token / expiresIn / 角色，**无密码字段**。
- 组件：`JwtTokenProvider`（签发与验签）、`JwtAuthenticationFilter`（写 SecurityContext）、
  `RestAuthenticationEntryPoint`（401）、`RestAccessDeniedHandler`（403）、`SecurityUtils`（当前用户）。
- 角色：USER / MERCHANT / ADMIN；`/admin/**` 仅 ADMIN，`/merchant/**` 允许 MERCHANT/ADMIN，
  阶段 4 起 `/merchant/products/**` 进一步收紧为仅 MERCHANT。`/demo/user|merchant|admin` 演示三级权限。
  方法级 `@PreAuthorize` 与 URL 级规则双保险。
- 阶段 2 的 `X-Dev-Admin` 临时保护已移除，管理端改用真实 JWT 角色鉴权。

**前端**：登录页、注册页、个人中心（真实数据）；Pinia 管理用户信息与角色；Axios 自动带 Bearer Token 并统一处理 401；
路由守卫按登录态与角色控制访问（仅体验，不替代后端鉴权）；头部与后台支持退出登录。

### 接口调用示例

```bash
# 1. 注册（角色由服务端固定分配 USER）
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"panda001","password":"Test@12345","confirmPassword":"Test@12345"}'

# 2. 登录，取 data.token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"panda001","password":"Test@12345"}' | python -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 3. 携带 Token 访问受保护接口
curl http://localhost:8080/api/v1/auth/me -H "Authorization: Bearer $TOKEN"

# 4. 无 Token / 无效 / 过期 → 401；USER 访问 /api/v1/demo/admin → 403
```

**Token 过期处理**：返回 HTTP 401 + `{code:401}`；前端 Axios 拦截器清除本地 Token 并跳转登录页（携带 redirect），
登录后自动回到原页面。重新登录即可获得新 Token。

### 验证命令

```bash
# 后端：编译 + 测试（需设置测试库环境变量）
cd backend && PANDAGO_TEST_DB_URL=... PANDAGO_TEST_DB_USER=... PANDAGO_TEST_DB_PASSWORD=... mvn clean test

# 前端：类型检查 + 生产构建
cd frontend && npm install && npm run build
```

后端测试共 **107 个**：上下文与健康检查 2 个、分类/商品集成 8 个、认证集成 12 个、
阶段 4 商品流程 14 个、阶段 5 用户中心 13 个、阶段 6 订单 16 个、阶段 7 状态与支付 12 个、
阶段 8 发货/确认收货/评价/管理端 15 个、阶段 9 缓存与超时订单 15 个。
阶段 7 覆盖 11 项指定场景（正常/重复/并发支付、取消支付订单、他人订单、正常/重复/并发取消、
库存只回补一次、已支付不可取消、支付事务回滚）与支付/状态查询归属校验。
阶段 8 覆盖 13 项指定场景（角色越权、资源归属、状态流转、唯一索引评价、审核留痕）及 2 项补充场景。
阶段 9 覆盖 10 项缓存场景（命中/未命中/失效/降级）与 5 项超时关闭场景（正常/未超时/已支付/重复执行/事务回滚）。
阶段 2/3/4/5 的集成测试用 `@Transactional` 自动回滚，不污染初始化数据；并发用例在 `finally` 中清理测试数据；
阶段 6/7/8/9 事务与并发回滚用例不使用外层事务，改为在 `@AfterEach` 中清理随机数据。

## 阶段 4 已交付内容

**后端**：
- 用户端：`GET /categories`、`GET /products`（关键词、分类、价格区间、排序白名单、分页）、`GET /products/hot`、`GET /products/{id}`。
  仅返回审核通过 + 上架 + 未删除商品；非法排序字段返回 400。
- 商家端：`GET/POST/PUT /merchant/products`、详情、上下架、提交审核。角色由 URL + `@PreAuthorize` + Service 三层校验，
  `merchantId` 只从 SecurityContext 读取，禁止修改他人商品。
- 管理端：`GET /admin/products`、`PUT /admin/products/{id}/audit`；驳回时强制下架并保存原因，通过时不代商家自动上架。
- 图片：`ProductImageWriter` 按白名单校验 URL 前缀、扩展名、长度、声明大小；主图进 `product.main_image`，主图/详情图进 `product_image`，同事务写入。
  **尚未实现文件上传**，不声称校验了真实文件字节流。
- 数据库：`audit_reason` 字段与 `idx_product_merchant` 索引，迁移脚本可重复执行。

**前端**：
- 首页分类导航与 `GET /products/hot` 热门推荐；列表页增加页内搜索、分类（含二级）、价格区间、排序、分页以及加载/空/错误三态。
- 商品详情页展示多图、价格、库存、销量；商家「我的商品」支持新增/编辑/上架/下架/提交审核与筛选；
  管理端「商品审核」支持通过/驳回并填写原因。
- 关键 API 封装在 `src/api/product.ts`，类型集中在 `src/types/catalog.ts`。

**测试结果**：`mvn test` 36/36 通过，`npm run build` 成功，公开接口 HTTP 联调与无头 Chrome 页面渲染检查通过。
完整记录见 `docs/STAGE4_TESTS.md`；API 见 `docs/API.md`；教学讲解与练习见 `docs/STAGE4_LEARNING.md`。

### 阶段 4 演示流程

```bash
# 0. 一次性导入本地演示账号（仅开发环境）
mysql --default-character-set=utf8mb4 < database/demo-stage4-users.sql

# 1. 浏览器登录 http://localhost:5173/login
#    账号 merchant_demo / 密码 Panda@12345 -> 进入 /merchant/products 新增商品并提交审核
# 2. 退出后使用 admin_demo 登录 -> /admin/audit 审核通过
# 3. 退出后使用 merchant_demo 登录 -> 将商品上架
# 4. 游客访问 /products 可搜索、筛选并进入详情
```

> 若 Windows 环境下 esbuild 报 `Access is denied`（系统临时目录权限受限），
> 可把临时目录指向项目内再构建：
> `TMPDIR=./.esbuild-tmp TEMP=./.esbuild-tmp npm run build`（PowerShell 用 `$env:TMPDIR=...`）。
> 开发服务器 `npm run dev` 不受此问题影响。

> 阶段 1 不连数据库；阶段 2 起连接 MySQL。阶段 9 起支持 Redis 缓存（`PANDAGO_CACHE_ENABLED=true` 开启），
> 未开启或无 Redis 时自动降级直查 MySQL，不影响核心交易。
> `docker-compose.yml` 提供容器化 MySQL / Redis（本机无 Docker 时可直接使用本机 MySQL 8）。

## 阶段 5 已交付内容

**后端**：
- 购物车：`GET /cart`、`POST /cart/items`、`PUT /cart/items/{id}`、`DELETE /cart/items/{id}`。
  `userId` 只从 SecurityContext 读取；唯一索引 + 原子 `INSERT ... ON DUPLICATE KEY UPDATE` 合并重复商品并防并发超卖；
  价格实时从 `product` 读取，购物车不存价格；下架/未审核/库存不足的条目标记 `available=false` 与原因，不计入预估金额。
- 收藏：`POST /favorites`、`DELETE /favorites/{productId}`、`GET /favorites`、`GET /favorites/check`。
  唯一索引防重复，跨用户删除返回 404。
- 地址：`GET/POST/PUT/DELETE /addresses`、`PUT /addresses/{id}/default`。参数校验齐全；
  生成列 + 唯一索引与事务保证每人最多一个默认地址；删除默认地址后不自动补位（理由见 `docs/API.md`）。
- 数据库：新增 `cart_item` / `favorite` / `user_address` 三表与唯一索引，`V2` 迁移脚本可重复执行。

**前端**：购物车页（数量增减、勾选、预估金额、失效提示）、商品卡片与详情页收藏按钮、收藏列表页、
地址列表/新增/编辑/删除/默认设置；登录态、加载、空数据与错误状态齐全。

**测试结果**：`mvn test` 49/49 通过，`npm run build` 成功；10 项指定场景与 HTTP 联调通过；
无头 Chrome 确认路由守卫与商品详情页新控件渲染正常。详见 `docs/STAGE5_TESTS.md`。

## 阶段 6 已交付内容

**后端**：
- `POST /orders`：只接收购物车条目 ID 或商品 ID+数量、地址 ID、备注；不接收任何价格与身份字段。
  校验地址归属、商品存在/上架/审核通过/同商家、数量与实时库存；金额用 `BigDecimal` 后端计算。
- 事务：事务边界在独立的 `OrderTransactionService.commit()` 上（跨 Bean 调用避开自调用陷阱），
  `@Transactional(rollbackFor = Exception.class)` 覆盖条件扣库存 → 订单主表 → 明细快照 → 清理购物车，
  任一步失败整体回滚；库存用条件 UPDATE + 受影响行数校验保证不超卖。
- `GET /orders`（分页/状态筛选）、`GET /orders/{id}`：仅返回本人订单；详情从地址与商品快照读取。
- 数据库：新增 `orders` / `order_item` 两张表及索引，`V3` 迁移脚本可重复执行。

**前端**：结算确认页（地址选择、商品确认、备注、预估金额）、下单成功页、订单列表（状态筛选/分页）、
订单详情（快照展示）；购物车"去结算"已接入，金额明确标注"预览，以后端为准"。

**测试结果**：`mvn clean package` 65/65 通过并生成 jar，`npm run build` 成功；12 项指定场景
含三类事务回滚全部通过；HTTP 联调验证篡改价格被忽略、他人订单 404、联调数据已清理。
详见 `docs/STAGE6_TESTS.md`，教学要点见 `docs/STAGE6_LEARNING.md`。

## 开发阶段（严格按 AGENTS 规范推进）

1. ~~项目初始化、环境配置和基础页面框架~~（已完成）
2. ~~数据库设计、初始化数据和基础 CRUD~~（已完成）
3. ~~用户注册登录、JWT 和角色权限~~（已完成）
4. ~~商品分类、商品管理和商品搜索~~（已完成）
5. ~~购物车、收货地址和商品收藏~~（已完成）
6. ~~订单创建、订单快照和事务处理~~（已完成）
7. ~~库存并发控制、模拟支付和订单取消~~（已完成）
8. ~~商家发货、用户确认收货、商品评价与管理端功能~~（已完成）
9. ~~Redis 缓存和超时订单定时任务~~（已完成）
10. ~~测试、部署、文档完善和项目复盘~~（已完成）

## 阶段 7 已交付内容

**后端**：`OrderStatusMachine` 统一五态流转；`POST /api/v1/payments/mock` 模拟支付；
`POST /api/v1/orders/{id}/cancel` 条件取消并回补库存；订单状态/支付结果查询；
`payment_record` 唯一索引 + 条件 UPDATE + 同事务保证幂等。

**前端**：订单列表与详情增加支付/取消按钮、支付结果展示、按钮防重复点击；
新增模拟支付页；下单成功页可直接进入支付。

**文档与测试**：`database/migration/V4__stage7_payment.sql`、`docs/STAGE7_TESTS.md`、
`docs/STAGE7_LEARNING.md`、`docs/API.md` 与 `docs/DATABASE.md` 已同步。

## 阶段 8 已交付内容

**后端**：商家订单查询/详情/发货（条件状态更新 + 物流记录同事务）；用户确认收货（写入 `received_at`）；
商品评价（`uk_review_order_item` 唯一索引 + 归属/完成校验）；管理端用户管理、分类管理、商品审核
（记录审核人/时间/意见 + 操作日志）、平台商品/订单查询。全部管理端接口三重鉴权保护。
本阶段未改写阶段 6 订单创建与阶段 7 支付/取消逻辑，复用 `OrderStatusMachine` 新增 SHIP / CONFIRM_RECEIPT 流转。

**前端**：商家订单列表与发货表单、物流信息展示；用户确认收货按钮、评价表单与评价列表、我的评价页；
管理端用户/分类/商品/订单页面；按角色展示导航，后端独立鉴权。

**文档与测试**：`database/migration/V5__stage8_shipment_review_admin.sql`、`docs/STAGE8_TESTS.md`、
`docs/STAGE8_LEARNING.md`，`docs/API.md`、`docs/DATABASE.md` 已同步。全量 92/92 测试通过。

## 阶段 9 已交付内容

**后端**：Spring Data Redis（`spring-boot-starter-data-redis` + Lettuce），连接走环境变量、
密码不入库；`CacheService` 抽象 + `RedisCacheService`（Jackson JSON 序列化 + 全链路降级）/
`NoOpCacheService`；商品详情、分类列表、热门商品 Cache Aside 读缓存，写操作删缓存失效，
空值短 TTL 防穿透；`@EnableScheduling` + `OrderTimeoutScheduler` 分批扫描超时待支付订单，
条件 UPDATE（`status + created_at<=cutoff`）+ 同事务回补库存，重复执行幂等，日志不刷屏。
本阶段未改写阶段 6/7/8 任何业务逻辑，缓存是读路径旁路增强、超时关闭复用阶段 7 回补模型。

**前端**：无改动（缓存与定时任务对接口透明）。

**文档与测试**：`docs/STAGE9_TESTS.md`、`docs/STAGE9_LEARNING.md`，
`docs/API.md`、`docs/DATABASE.md` 已同步。全量 107/107 测试通过；无 Redis 环境下读接口降级联调 200。

## 阶段 10 已交付内容（最终验收）

**质量检查**：后端分层清晰，Controller 只做参数绑定/鉴权注解/Service 调用（最长 108 行且多为注释），
无业务逻辑下沉；Service 无有害重复；日志与配置无密码/Token/密钥泄露（密钥全部环境变量注入）；
统一异常处理覆盖业务异常码→HTTP 状态映射、参数校验、权限、未知资源。修复了一处真实缺陷：
Spring Boot 3.2 的 `NoResourceFoundException` 原被兜底成 500，现映射为 404。

**测试**：`mvn clean test` 全量 **118/118** 通过（原 107 + 阶段 10 新增 11）。新增
`Stage10UnitTests`（状态机纯逻辑 5 项）与 `Stage10QualityIntegrationTests`（统一异常/参数校验/
权限隔离/库存=1 两人并发购买防超卖/端到端一致性 6 项）。前端 `npm run type-check` 与
`npm run build` 均通过。端到端 14 步业务演示见 `docs/e2e_demo.py`，实测 **22/22** 全通过。
并发与一致性记录见 `docs/STAGE10_TESTS.md`。

**部署**：新增 `backend/Dockerfile`、`frontend/Dockerfile` + `nginx.conf`，扩充
`docker-compose.yml` 为 MySQL + Redis + backend + frontend 四服务编排（环境变量注入敏感配置、
命名卷持久化、healthcheck 与健康依赖），并提供 `.env.example`。部署说明见 `docs/DEPLOYMENT.md`。

**索引优化**：补 `idx_orders_status_created (status, created_at, id)` 覆盖索引，
超时扫描 EXPLAIN 由全表扫描变为 `Using index`；迁移脚本 `V6__stage10_order_timeout_index.sql` 幂等。

**文档**：`docs/DEPLOYMENT.md`、`docs/LEARNING_NOTES.md`、`docs/PROJECT_REVIEW.md`、
`docs/INTERVIEW_REVIEW.md`（12 个面试主题的问法/基于代码的回答/追问/代码位置）、
`docs/STAGE10_TESTS.md` 新增，`docs/API.md`、`docs/DATABASE.md` 同步。
最终验收报告见 `docs/FINAL_ACCEPTANCE.md`，端到端演示脚本见 `docs/e2e_demo.py`。

## 说明

- 本项目为教学项目，第一版支付为**模拟支付**，不涉及真实资金。
- 需求以 PRD 为准，技术实现以 TECH_DESIGN 为准，开发规范见 AGENTS。
