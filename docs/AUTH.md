# PandaGo 阶段 3：认证与授权教学笔记

> 对应代码：`com.pandago.auth.*`、`com.pandago.security.*`、`SecurityConfig`、
> `AccessControlDemoController`，前端 `stores/user.ts`、`api/request.ts`、`router/index.ts`。

---

## 1. 登录请求经过哪些 Spring Security 组件

```
POST /api/v1/auth/login  {username, password}
        │
        ▼
[Filter 链] CorsFilter → ... → JwtAuthenticationFilter（本次无 Token，跳过）
        │  （/login 是 permitAll 公开接口，直接放行到 Controller）
        ▼
[DispatcherServlet] → AuthController.login(@Valid LoginDTO)
        │
        ▼
[Service] AuthServiceImpl.login
        │  1) UserMapper 按用户名查用户
        │  2) 检查 status（禁用→403）
        │  3) passwordEncoder.matches(明文, BCrypt哈希)
        │  4) RoleMapper 查角色
        │  5) JwtTokenProvider.generateToken(...)   ← 签发 JWT
        ▼
Result<LoginVO> → Jackson 序列化 → HTTP 200 + JSON
```

**关键**：登录本身不走 `UsernamePasswordAuthenticationFilter`，而是我们自己的 Controller + Service
显式校验密码并签发 Token。Spring Security 在这里的作用是"放行公开接口"和"为后续请求做守卫"。
之后每次带 Token 的请求才会进入第 2 节的验证流程。

---

## 2. JWT 如何生成、携带和验证

```
【生成】登录成功
  userId + username + roles
    └─ Jwts.builder()
         .subject(userId)
         .claim("uid", userId).claim("username", ...).claim("roles", [...])
         .issuedAt(now).expiration(now + 24h)
         .signWith(密钥, HS256/384)      ← 密钥来自 PANDAGO_JWT_SECRET
       → 三段式字符串 header.payload.signature

【携带】前端每次请求
  Authorization: Bearer eyJhbGciOi...

【验证】JwtAuthenticationFilter（Controller 之前）
  取 Bearer Token
    → jwtTokenProvider.validate(token)
         ├─ 验签名（防止篡改）
         └─ 验过期时间（exp < now 则拒绝）
    → 解析 uid / username / roles
    → 组装 UsernamePasswordAuthenticationToken(principal, 权限列表)
    → SecurityContextHolder.getContext().setAuthentication(...)
    → 进入授权判断（角色是否匹配）
       ├─ 通过 → Controller → Service → Mapper → MySQL
       └─ 失败 → 401（未认证）/ 403（已认证但角色不足）
```

**JWT 载荷里绝不放密码**：Token 一旦签发就(base64)明文可见，放密码等同于泄露。
只放能标识身份的最小信息；角色放在 Token 里可避免每次请求查库，但角色变更需要重新登录才生效
（这是用无状态 Token 的取舍）。

---

## 3. Filter 与 Controller 的区别

| 维度 | Filter（过滤器） | Controller（控制器） |
| --- | --- | --- |
| 所处层级 | Servlet 容器层，进入 Spring MVC **之前** | Spring MVC 内，由 DispatcherServlet 分发 |
| 执行时机 | 每个请求都会经过（含静态资源） | 只有匹配 URL 的请求 |
| 能否拿到方法参数 | 否，只能读原始 HttpServletRequest | 是，能绑定 JSON、路径变量、校验参数 |
| 典型职责 | 认证、日志、CORS、限流 | 业务逻辑入口、参数校验、返回响应 |
| 本项目 | `JwtAuthenticationFilter` 解析 Token 写 SecurityContext | `AuthController` 处理注册/登录/me |

简单记：**Filter 负责"你是谁"，Controller 负责"你要做什么"**。
JWT 认证放在 Filter 而不是 Controller，是为了让所有业务接口无需重复写 Token 校验代码。

---

## 4. BCrypt 为什么不是普通加密

- **普通加密（如 AES）是 reversible 可逆的**：有密钥就能从密文还原明文。一旦密钥泄露，全部密码泄露。
- **BCrypt 是单向哈希，不可还原**：只能"用明文 + 哈希验证是否匹配"，无法从哈希倒推出密码。
- **自带随机盐（salt）**：每次对同一密码哈希，结果都不同（`encode("123456")` 两次结果不一样）。
  这样攻击者无法用彩虹表批量撞库，两个用户即便密码相同，哈希也不同。
- **可调节计算成本（work factor）**：故意设计得"慢"，让暴力破解成本极高。
- **适配性强**：哈希字符串里包含算法版本与成本，将来升级成本参数时旧密码仍能验证。

> 代码中体现：注册用 `passwordEncoder.encode(明文)` 存库；登录用
> `passwordEncoder.matches(明文, 哈希)` 校验。两者必须用同一个 `PasswordEncoder` Bean。
> 数据库里存的是 `$2a$...` 开头的哈希串，绝不是明文。

---

## 5. Authentication 和 SecurityContext 的作用

- **Authentication**：一次"认证请求/认证结果"的载体。里面有
  `principal`（主体身份，本项目是 `LoginUserPrincipal{userId, username}`）、
  `credentials`（凭证，密码——认证成功后会被清空）、
  `authorities`（权限列表，如 `ROLE_USER`）。
- **SecurityContext**：绑定当前线程的"安全上下文"，里面就放着那一个 `Authentication`。
- 工作流程：`JwtAuthenticationFilter` 验签成功后构造 `Authentication` 并
  `SecurityContextHolder.getContext().setAuthentication(auth)`；
  之后同一次请求里，任何代码都能通过 `SecurityUtils.requireCurrentUser()` 从上下文拿到当前用户，
  `@PreAuthorize("hasRole('ADMIN')")` 也从这里读取角色做判断。
- 请求结束后 Spring Security 会清理线程上下文，避免串号（线程池复用线程）。

**安全要点**：当前用户身份只能从 SecurityContext 取，**绝不能相信客户端传来的 userId**。
`SecurityUtils` 就是这个原则的落地——它不接受任何参数。

---

## 6. 角色权限与资源归属校验的区别

这是最容易踩的越权漏洞（IDOR），务必分清：

| | 角色校验（你是谁） | 资源归属校验（这是不是你的东西） |
| --- | --- | --- |
| 回答的问题 | 你有没有"访问这类接口"的资格 | 你要操作的"这条具体数据"属不属于你 |
| 实现方式 | `hasRole('ADMIN')` / URL 规则 | 查询/更新条件拼 `owner_id = 当前用户 id` |
| 例子 | 只有 ADMIN 能进 `/admin/**` | 商家 A 不能改商家 B 的商品 |
| 缺失后果 | 普通用户能进管理后台 | 登录用户能看/改别人的订单、地址、商品 |

**两者都要有，缺一不可**。通过了角色校验不代表可以操作任意数据。

通用落地思路（后续订单/商品阶段实现）：
1. 用 `SecurityUtils.requireCurrentUser()` 取当前用户 id，不读客户端传的 userId；
2. 查询/更新语句强制带上归属条件，如 `WHERE id = #{id} AND user_id = #{currentUserId}`；
3. 检查影响行数，为 0 说明不存在或不归属自己，统一返回 404/403，不泄露"数据存在但不属于你"；
4. ADMIN 可走独立的可审计 `_admin` 接口跳过归属过滤。

演示代码见 `AccessControlDemoController` 末尾注释。
测试 `AuthIntegrationTests.userCannotAccessAdminEndpoint` 验证了角色校验这一层。

---

## 阶段练习（先自己做，再看代码核对）

**基础题**
1. 为什么 JWT 里不能放密码哈希？
2. `BCryptPasswordEncoder.encode("123456")` 连续调用两次结果相同吗？为什么？
3. STATELESS 会话策略下，服务端靠什么识别用户？

**代码阅读题**
4. 阅读 `JwtAuthenticationFilter`：为什么 Token 无效时不直接返回 401，而是放行给后续组件？
5. 阅读 `SecurityConfig`：为什么 `GET /api/v1/products` 要 permitAll，而 `/admin/**` 要 hasRole？
6. 阅读 `AuthServiceImpl.register`：数据库唯一索引在哪一步真正发挥作用？

**独立编码练习**
7. 给 `/demo/user` 增加一个接口 `/demo/profile`，只返回当前登录用户的 username 与 id，
   要求身份从 `SecurityUtils` 获取，不接收任何 userId 参数。

**面试题**
8. 简述 JWT 的组成和防篡改原理。
9. Filter 和 Interceptor 的区别？Spring Security 的过滤器链和 MVC 拦截器的执行顺序是怎样的？
