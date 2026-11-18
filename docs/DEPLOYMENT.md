# PandaGo 部署说明（Docker Compose）

本文档描述用 Docker Compose 一键部署 PandaGo 全栈（MySQL + Redis + 后端 + 前端）的步骤。
所有敏感配置通过环境变量注入，仓库中不含任何生产密钥。

## 1. 前置条件

- Docker 24+ 与 Docker Compose v2（命令为 `docker compose`，非旧版 `docker-compose`）
- 服务器开放端口：默认 `80`（前端）、`8080`（后端）、可选 `3306`/`6379`（数据库/缓存，建议生产不对外暴露）
- 生成强随机密钥的工具（如 `openssl`）

## 2. 配置环境变量

```bash
cp .env.example .env
```

编辑 `.env`，**必须修改所有 `CHANGE_ME` 占位值**：

| 变量 | 说明 | 生成方式 |
| --- | --- | --- |
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码（初始化与 healthcheck） | `openssl rand -base64 32` |
| `MYSQL_PASSWORD` | 应用连接 MySQL 的密码（账号默认 pandago） | `openssl rand -base64 32` |
| `REDIS_PASSWORD` | Redis 访问密码 | `openssl rand -base64 32` |
| `PANDAGO_JWT_SECRET` | JWT 签名密钥，**>=32 字节** | `openssl rand -base64 48` |
| `PANDAGO_CACHE_ENABLED` | 是否启用 Redis 缓存（默认 true） | — |
| `MYSQL_HOST_PORT` / `REDIS_HOST_PORT` / `BACKEND_HOST_PORT` / `FRONTEND_HOST_PORT` | 宿主机映射端口 | 按需 |

> 生产环境绝不要沿用 `.env.example`、demo 脚本或本地开发配置里的任何密码/JWT 密钥。
> `application-prod.yml` 对 `PANDAGO_JWT_SECRET` **不设默认值**，缺失时应用直接启动失败，避免误用弱密钥。

## 3. 启动

```bash
# 校验配置（会展开环境变量，检查挂载文件是否存在）
docker compose config

# 构建镜像并后台启动
docker compose up -d --build

# 查看服务状态（等待全部 healthy）
docker compose ps
```

首次启动时 MySQL 会执行 `docker-entrypoint-initdb.d` 下的 `schema.sql` 与 `init-data.sql`
（14 张表 + 分类/商品基础数据），**不会** 创建任何带已知密码的用户账号。

## 4. 验证健康检查

```bash
# 各服务健康状态：mysql/redis/backend 应显示 healthy
docker compose ps

# 后端健康端点（经宿主机映射）
curl http://localhost:8080/api/v1/health
# 期望 data.status = "UP"

# 前端首页
curl -I http://localhost/
```

compose 中 healthcheck 定义：
- MySQL：`mysqladmin ping`
- Redis：`redis-cli -a "$REDIS_PASSWORD" ping`
- 后端：`curl http://localhost:8080/api/v1/health | grep UP`

## 5. 常用运维命令

```bash
# 查看日志（全部 / 单服务 / 实时跟随）
docker compose logs
docker compose logs backend
docker compose logs -f backend

# 停止 / 启动 / 重启
docker compose stop
docker compose start
docker compose restart backend

# 停止并移除容器（保留数据卷）
docker compose down

# 停止并移除容器 + 数据卷（⚠️ 会删除数据库数据，不可逆）
docker compose down -v

# 重建某个服务（改了 Dockerfile 或代码后）
docker compose up -d --build backend

# 进入容器排查
docker compose exec backend sh
docker compose exec mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" pandago
```

## 6. 数据持久化

- `mysql-data` 卷：`/var/lib/mysql`，MySQL 数据文件
- `redis-data` 卷：`/data`，Redis AOF 持久化（`--appendonly yes`）

`docker compose down` 默认保留卷；只有显式 `-v` 才删除。备份示例：

```bash
docker compose exec mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" pandago > backup.sql
```

## 7. 旧库增量升级（非全新部署）

全新容器用 `schema.sql` 一次初始化到位；已存在的旧库按顺序执行迁移（均幂等）：

```bash
for f in database/migration/V*.sql; do
  mysql --default-character-set=utf8mb4 "$f"
done
```

V6 为阶段 10 新增的超时扫描优化索引 `(status, created_at, id)`。

## 8. 初始化业务账号

生产环境默认**无用户**。通过注册接口创建账号后，若需商家/管理员角色，用 DBA 工具在
`user_role` 表插入对应角色关联（角色见 `role` 表：USER / MERCHANT / ADMIN）。
本地教学演示可导入 `database/demo-stage4-users.sql`（账号 `merchant_demo` / `admin_demo` /
`user_demo`，密码 `Panda@12345`）——**该脚本仅开发环境使用，禁止在生产执行**。

## 9. 已知限制（未实现，不得视为已交付）

- 支付为**模拟支付**，无真实支付渠道回调与对账。
- 商品图片使用 URL 白名单，无文件上传接口与对象存储。
- 物流只有"公司 + 单号"，无真实快递轨迹查询。
- 未接入 knife4j/Swagger 在线接口文档（接口说明见 `docs/API.md`）。
- 本环境验证情况：本机无 Docker 引擎，容器构建与健康检查**未能实机执行**；已完成
  compose YAML 语法校验、配置自洽性与 prod profile 真实启动验证（健康端点返回 UP）。
