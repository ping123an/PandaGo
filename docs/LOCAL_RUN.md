# PandaGo 本地运行指南

本指南只讲一件事：**如何把 PandaGo 在自己电脑上跑起来**。从环境准备到前后端启动、验证、停止，按顺序执行即可。

- 通用步骤适用于任何装了 JDK 17 / Node 20+ / MySQL 8 的机器。
- 标注「**当前机器**」的部分是本项目开发机（Windows + Git Bash）的实测配置，可直接复制命令。
- 想用 Docker 一键启动的，直接看文末「方式二：Docker」；本机当前无 Docker 引擎，该方式未实机验证。

---

## 一、环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17+ | 编译运行后端 |
| Maven | 3.9+ | 构建后端（或用 Maven Wrapper） |
| Node.js | 20+（实测 22） | 构建/启动前端 |
| npm | 10+ | 安装前端依赖 |
| MySQL | 8.0 | 存储业务数据 |
| Redis | 7（可选） | 缓存；**不装也能跑**，应用自动降级直查 MySQL |

验证版本：

```bash
java -version
mvn -version
node -v && npm -v
```

---

## 二、准备数据库

### 2.1 准备一个 MySQL 实例

任选其一：

- **已有 MySQL**：确保能用一个账号连接，并拥有建库建表权限。
- **当前机器**：系统 MySQL 在 `3306` 但没有可用凭据，项目已在 `3307` 起了一个独立实例（空密码、root），数据目录 `.workbuddy/tmp/mysql-data`。若它没在运行，用下面命令启动（Git Bash）：

```bash
"/c/Program Files/MySQL/MySQL Server 8.0/bin/mysqld.exe" \
  --datadir="D:/AI_example/DH_test/Mark/.workbuddy/tmp/mysql-data" \
  --basedir="C:/Program Files/MySQL/MySQL Server 8.0" \
  --port=3307 \
  --socket="D:/AI_example/DH_test/Mark/.workbuddy/tmp/mysql.sock" \
  --console
```

确认连接（当前机器用 3307 + 空密码）：

```bash
"/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql" -h127.0.0.1 -P3307 -uroot --default-character-set=utf8mb4 -e "SELECT VERSION();"
```

### 2.2 初始化表和数据

在**项目根目录**执行（`<` 是输入重定向，Windows PowerShell 请用 `Get-Content -Encoding utf8 file.sql | mysql ...`）：

```bash
MYSQL="/c/Program Files/MySQL/MySQL Server 8.0/bin/mysql"
"$MYSQL" -h127.0.0.1 -P3307 -uroot --default-character-set=utf8mb4 < database/schema.sql
"$MYSQL" -h127.0.0.1 -P3307 -uroot --default-character-set=utf8mb4 < database/init-data.sql
```

- `schema.sql`：建库 `pandago` + 14 张表（含阶段 10 的超时扫描索引）。
- `init-data.sql`：角色、分类、商品等基础数据。
- 两个脚本都可**重复执行**（`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE`），不会清空已有数据。

> ⚠️ 必须带 `--default-character-set=utf8mb4`，否则 Windows 默认 GBK 会把中文写成乱码。

### 2.3 导入演示账号（可选，仅本地）

需要体验商家端/管理端时，导入带已知密码的演示账号：

```bash
"$MYSQL" -h127.0.0.1 -P3307 -uroot --default-character-set=utf8mb4 < database/demo-stage4-users.sql
```

| 账号 | 角色 | 密码 |
| --- | --- | --- |
| `user_demo` | 普通用户 | `Panda@12345` |
| `merchant_demo` | 商家 | `Panda@12345` |
| `admin_demo` | 管理员 | `Panda@12345` |

> 该脚本**只能本地开发用**，禁止在生产环境执行。普通用户也可通过注册接口自行创建。

### 2.4 已有旧库时执行增量迁移

如果数据库是更早阶段建的，按顺序补跑迁移（均幂等）：

```bash
for f in database/migration/V*.sql; do
  "$MYSQL" -h127.0.0.1 -P3307 -uroot --default-character-set=utf8mb4 < "$f"
done
```

---

## 三、启动后端

在项目根目录下，用环境变量注入数据库凭据（凭据不写进代码）：

```bash
cd backend

export PANDAGO_DB_URL='jdbc:mysql://127.0.0.1:3307/pandago?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
export PANDAGO_DB_USER=root
export PANDAGO_DB_PASSWORD=
# 可选：自定义 JWT 密钥（>=32 字节）；不设则用本地开发兜底值
export PANDAGO_JWT_SECRET='pandago-local-dev-secret-key-at-least-32-bytes-long'

mvn spring-boot:run
```

或者先打包再运行 jar：

```bash
mvn package -DskipTests
java -jar target/pandago-backend-0.0.1-SNAPSHOT.jar
```

启动成功的标志（日志出现）：

```
Started PandaGoApplication in N seconds
```

后端默认端口 `8080`，接口前缀 `/api/v1`。

> **说明**：本机没有 Redis，无需任何额外配置——缓存默认关闭（`PANDAGO_CACHE_ENABLED=false`），
> 应用自动走 `NoOpCacheService` 直查 MySQL，功能不受影响。想启用缓存时再装 Redis 并设该变量为 true。

---

## 四、启动前端

另开一个终端：

```bash
cd frontend
npm install
npm run dev
```

访问 <http://localhost:5173>。开发模式下 `/api` 请求会自动代理到后端 `8080`。

> **当前机器注意**：本机 esbuild 偶发临时目录权限问题，若 `npm run dev` 或 `npm run build` 报 esbuild 相关错误，
> 先把临时目录指向项目内目录再启动：
> ```bash
> export TMPDIR="$PWD/.esbuild-tmp" TEMP="$PWD/.esbuild-tmp" TMP="$PWD/.esbuild-tmp"
> mkdir -p .esbuild-tmp
> ```

---

## 五、验证是否跑通

```bash
# 1. 后端健康检查
curl http://localhost:8080/api/v1/health
# 期望 data.status = "UP"

# 2. 公开接口
curl http://localhost:8080/api/v1/categories
curl "http://localhost:8080/api/v1/products?pageNum=1&pageSize=12"

# 3. 前端页面
# 浏览器打开 http://localhost:5173，应看到首页分类和商品
```

浏览器里用演示账号登录（`/login`），即可体验对应角色的功能。

**可选：跑一次完整业务演示**（需先装 `requests`）：

```bash
pip install requests
python docs/e2e_demo.py --base http://localhost:8080
# 期望 22/22 步通过
```

> 演示会新建商品/订单/用户，脚本末尾给出清理 SQL；生产或长期使用的库请勿在数据干净之外随意运行。

---

## 六、运行测试

```bash
# 后端全量测试（需要先启动 3307 MySQL，且测试库有 pandago 库）
cd backend
export PANDAGO_TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/pandago?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
export PANDAGO_TEST_DB_USER=root
export PANDAGO_TEST_DB_PASSWORD=
mvn clean test
# 期望 118/118 通过

# 前端类型检查与构建
cd frontend
npm run type-check
npm run build
```

---

## 七、停止服务

- 前端：在终端按 `Ctrl + C`。
- 后端：在终端按 `Ctrl + C`。
- MySQL 独立实例（当前机器，PID 见 `netstat -ano | findstr :3307`）：

```bash
# Git Bash 用 taskkill 结束对应 PID
taskkill //PID 25280 //F
```

---

## 八、常见问题

| 现象 | 原因 / 解决 |
| --- | --- |
| 后端启动报 `Access denied for user` | 数据库账号密码环境变量不对，核对 `PANDAGO_DB_USER/PASSWORD` |
| 后端连不上数据库 | `PANDAGO_DB_URL` 端口写错；当前机器是 `3307`，不是默认的 `3306` |
| 中文乱码 | 执行 SQL 时没加 `--default-character-set=utf8mb4` |
| `mvn clean` 失败，提示 jar 被占用 | 有旧后端进程在用 jar，先停掉它（`netstat -ano | findstr :8080` 找 PID 后 `taskkill //PID xxx //F`） |
| 前端报 esbuild 权限错误 | 按第四节设置 `TMPDIR/TEMP/TMP` 指向 `.esbuild-tmp` |
| 页面能开但接口 500 | 看后端日志；多半是数据库没初始化或环境变量没注入 |
| 想启用 Redis 缓存 | 装 Redis 7，设 `PANDAGO_CACHE_ENABLED=true` 及 `PANDAGO_REDIS_HOST/PORT/PASSWORD` |

---

## 方式二：Docker 一键启动（需 Docker）

本机当前无 Docker 引擎，此方式**未实机验证**；在有 Docker 的机器上：

```bash
cp .env.example .env
# 编辑 .env，修改所有 CHANGE_ME 密码与 JWT 密钥
docker compose config          # 校验配置
docker compose up -d --build   # 构建并后台启动
docker compose ps              # 等 mysql/redis/backend 全部 healthy
curl http://localhost:8080/api/v1/health   # data.status = UP
```

前端在 `http://localhost/`，后端经 Nginx 反代 `/api`。停止用 `docker compose down`（加 `-v` 会删数据，慎用）。
详细命令与说明见 `docs/DEPLOYMENT.md`。

---

## 相关文档

- `README.md` — 项目总览、技术栈、阶段交付
- `docs/DEPLOYMENT.md` — Docker 部署与运维命令
- `docs/API.md` — 接口清单与错误码
- `docs/STAGE10_TESTS.md` — 测试与端到端验证记录
- `docs/FINAL_ACCEPTANCE.md` — 最终验收报告与未实现功能清单
