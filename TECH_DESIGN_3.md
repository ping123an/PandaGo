@[toc]

# TECH_DESIGN

# Shop 在线商城技术设计文档（TECH_DESIGN）

> 文档版本：V1.0
> 项目名称：Shop
> 项目类型：综合型在线商城购物平台
> 架构模式：前后端分离 + 模块化单体架构
> 后端技术：Java 17 + Spring Boot 3
> 前端技术：Vue 3 + TypeScript
> 数据库：MySQL 8
> 缓存：Redis 7
> 参考文档：PRD.md

# 一、技术架构设计

## 1.1 架构目标

Shop 采用前后端分离的模块化单体架构。

项目主要目标：

1. 保证前后端职责清晰。
2. 方便不同业务模块独立开发和维护。
3. 保证订单、库存等核心业务的数据一致性。
4. 控制项目复杂度，适合 Java 初级后端学习。
5. 为后续功能扩展预留空间。

第一阶段不采用微服务架构，不引入复杂的分布式组件。

## 1.2 系统整体架构

系统由以下部分组成：

```
                    用户浏览器
                        |
                        v
              Vue 3 前端应用
                        |
                    Axios
                        |
                  HTTP / JSON
                        |
                        v
              Spring Boot 后端
                        |
       +----------------+----------------+
       |                |                |
       v                v                v
    Controller       Security          Common
       |                |                |
       +----------------+----------------+
                        |
                        v
                    Service
                        |
       +----------------+----------------+
       |                |                |
       v                v                v
     Mapper          Redis            其他服务
       |
       v
     MySQL
```

## 1.3 系统模块划分

系统分为三个业务端：

### 用户端

负责普通用户的购物功能：

- 首页
- 商品分类
- 商品搜索
- 商品详情
- 购物车
- 收货地址
- 商品收藏
- 订单管理
- 模拟支付
- 商品评价
- 个人中心

### 商家端

负责商家商品和订单管理：

- 商家工作台
- 商品管理
- 商品上下架
- 商品审核状态
- 库存管理
- 商家订单
- 订单发货
- 商家资料

### 管理端

负责平台运营：

- 管理员工作台
- 用户管理
- 商品分类管理
- 商品审核
- 商品管理
- 订单管理
- 公告管理（后续版本）

# 二、技术栈选择

## 2.1 后端技术栈

| 技术              | 版本建议       | 使用目的             |
| ----------------- | -------------- | -------------------- |
| Java              | 17             | 后端开发语言         |
| Spring Boot       | 3.x            | 后端应用框架         |
| Spring MVC        | 随 Spring Boot | 接收和处理 HTTP 请求 |
| Spring Security   | 6.x            | 身份认证和权限控制   |
| JWT               | 兼容版本       | 用户登录身份令牌     |
| MyBatis-Plus      | 3.x            | 数据库访问和 CRUD    |
| MySQL             | 8.x            | 关系型数据库         |
| Redis             | 7.x            | 缓存和辅助业务控制   |
| Maven             | 3.9+           | 项目依赖管理         |
| Lombok            | 兼容版本       | 减少样板代码         |
| Knife4j / OpenAPI | 兼容版本       | API 接口文档         |
| JUnit 5           | 兼容版本       | 单元测试             |
| Mockito           | 兼容版本       | 模拟依赖进行测试     |

### 后端技术选择原因

**Java 17**

使用 LTS 版本，学习现代 Java 开发特性，满足 Spring Boot 3 的运行要求。

**Spring Boot 3**

负责应用启动、依赖注入、配置管理、Web 请求处理和业务组件管理。

**Spring Security + JWT**

实现用户登录认证和角色权限控制。

**MyBatis-Plus**

简化常见数据库 CRUD 操作，同时保留编写 SQL 的能力，便于学习数据库查询和优化。

**MySQL 8**

存储用户、商品、购物车、订单和支付等核心业务数据。

**Redis**

用于商品详情缓存、分类缓存、热点数据缓存，以及后续的限流等功能。

注意：库存正确性必须由数据库保证，不能仅依赖 Redis。

## 2.2 前端技术栈

| 技术         | 用途                |
| ------------ | ------------------- |
| Vue 3        | 前端页面框架        |
| TypeScript   | JavaScript 类型约束 |
| Vite         | 项目构建工具        |
| Vue Router   | 页面路由管理        |
| Pinia        | 全局状态管理        |
| Axios        | HTTP 请求           |
| Element Plus | UI 组件库           |
| SCSS / CSS   | 页面样式            |

### 前端技术选择原因

- Vue 3 适合构建组件化页面。
- TypeScript 提高代码可维护性。
- Vite 提供快速开发和构建能力。
- Vue Router 管理用户端、商家端和管理端路由。
- Pinia 保存用户登录信息和购物车相关状态。
- Axios 统一处理 HTTP 请求和 Token。
- Element Plus 提供后台管理所需的表格、表单和弹窗组件。

## 2.3 开发工具

| 工具                                                  | 用途           |
| ----------------------------------------------------- | -------------- |
| IntelliJ IDEA                                         | Java 后端开发  |
| WebStorm / VS Code                                    | 前端开发       |
| MySQL Workbench / Navicat                             | 数据库管理     |
| Redis Desktop Manager / Another Redis Desktop Manager | Redis 管理     |
| Apifox / Postman                                      | API 测试       |
| Git                                                   | 版本管理       |
| Docker Desktop                                        | 本地服务和部署 |
| Maven                                                 | 后端项目构建   |
| Node.js                                               | 前端运行环境   |

## 2.4 技术选型原则

1. 优先使用成熟、常见的技术。
2. 优先选择 Java 初级后端岗位常见技术。
3. 避免引入不必要的中间件。
4. 保证项目能够在本地正常运行。
5. 每个技术都需要理解基本原理，不能只会调用。

# 三、项目结构设计

## 3.1 项目根目录

建议采用前后端分离的目录结构：

```
ShopSphere/
│
├── backend/                    # Spring Boot 后端
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/shopsphere/
│   │   │   │       ├── ShopSphereApplication.java
│   │   │   │       │
│   │   │   │       ├── common/
│   │   │   │       │   ├── api/
│   │   │   │       │   ├── config/
│   │   │   │       │   ├── exception/
│   │   │   │       │   ├── enums/
│   │   │   │       │   ├── constant/
│   │   │   │       │   └── util/
│   │   │   │       │
│   │   │   │       ├── security/
│   │   │   │       ├── auth/
│   │   │   │       ├── user/
│   │   │   │       ├── category/
│   │   │   │       ├── product/
│   │   │   │       ├── cart/
│   │   │   │       ├── address/
│   │   │   │       ├── favorite/
│   │   │   │       ├── order/
│   │   │   │       ├── payment/
│   │   │   │       ├── shipment/
│   │   │   │       ├── review/
│   │   │   │       ├── merchant/
│   │   │   │       └── admin/
│   │   │   │
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       ├── application-dev.yml
│   │   │       ├── application-test.yml
│   │   │       └── mapper/
│   │   │
│   │   └── test/
│   │       └── java/
│   │           └── com/shopsphere/
│   │
│   ├── pom.xml
│   └── Dockerfile
│
├── frontend/                   # Vue 3 前端
│   ├── src/
│   │   ├── api/
│   │   ├── assets/
│   │   ├── components/
│   │   ├── layouts/
│   │   ├── router/
│   │   ├── stores/
│   │   ├── types/
│   │   ├── utils/
│   │   ├── views/
│   │   │   ├── user/
│   │   │   ├── merchant/
│   │   │   └── admin/
│   │   ├── App.vue
│   │   └── main.ts
│   │
│   ├── package.json
│   └── vite.config.ts
│
├── database/
│   ├── schema.sql
│   ├── init-data.sql
│   └── migration/
│
├── docs/
│   ├── PRD.md
│   ├── TECH_DESIGN.md
│   ├── API.md
│   └── DATABASE.md
│
├── docker-compose.yml
├── .gitignore
└── README.md
```

## 3.2 后端业务模块内部结构

每个业务模块统一采用分层结构：

```
product/
├── controller/
│   └── ProductController.java
│
├── service/
│   ├── ProductService.java
│   └── impl/
│       └── ProductServiceImpl.java
│
├── mapper/
│   └── ProductMapper.java
│
├── entity/
│   └── Product.java
│
├── dto/
│   ├── ProductCreateDTO.java
│   ├── ProductUpdateDTO.java
│   └── ProductQueryDTO.java
│
├── vo/
│   └── ProductDetailVO.java
│
└── enums/
    └── ProductStatus.java
```

### 各层职责

**Controller 层**

负责：

- 接收 HTTP 请求。
- 获取请求参数。
- 调用 Service。
- 返回统一响应结果。

Controller 不负责复杂业务逻辑。

**Service 层**

负责：

- 实现核心业务逻辑。
- 业务规则校验。
- 调用 Mapper。
- 控制事务。
- 协调多个业务对象。

**Mapper 层**

负责：

- 数据库查询。
- 数据库新增。
- 数据库修改。
- 数据库删除。

复杂 SQL 可以写在 Mapper XML 中。

**Entity 层**

用于映射数据库表。

**DTO 层**

用于接收前端提交的数据。

**VO 层**

用于返回前端需要展示的数据。

### 分层调用规则

```
Controller
    ↓
Service
    ↓
Mapper
    ↓
MySQL
```

不允许 Controller 直接操作 Mapper 完成复杂业务。

不允许 Mapper 承担业务判断。

Service 层负责核心业务逻辑。

# 四、数据库设计

## 4.1 数据库基本规范

数据库名称：

```
shop
```

字符集：

```
utf8mb4
```

排序规则：

```
utf8mb4_0900_ai_ci
```

数据库引擎：

```

```