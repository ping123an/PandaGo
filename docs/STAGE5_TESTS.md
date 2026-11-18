# 阶段 5 测试报告：购物车、收藏与收货地址

> 测试日期：2026-11-17。环境：本地隔离 MySQL 8.0.22（127.0.0.1:3307，库 `pandago`）、
> JDK 17、Spring Boot 3、后端 8080、前端 5173。演示账号由 `database/demo-stage4-users.sql` 创建。

## 1. 用户指定的 10 项场景

| # | 场景 | 实现方式 | 真实结果 |
| --- | --- | --- | --- |
| 1 | 未登录访问 | 无 Token 请求 `/cart` `/favorites` `/addresses` | ✅ 均返回 HTTP 401 |
| 2 | 添加同一商品多次 | 连续 POST 数量 2 和 3，再查购物车 | ✅ 合并为一行，数量 5，库中只有一条记录 |
| 3 | 添加下架商品 | 插入 `OFF_SALE` 商品后 POST 购物车 | ✅ 返回 409，消息含“下架”，购物车无新增行 |
| 4 | 修改数量为 0 或负数 | PUT quantity=0 / -1 | ✅ 均返回 400（`@Min(1)` 参数校验）；quantity=99 超库存返回 409 |
| 5 | 操作其他用户购物车记录 | USER_B 对 USER_A 的条目 PUT / DELETE | ✅ 均返回 404，A 的条目数量未变 |
| 6 | 重复收藏 | 连续 POST 相同 productId | ✅ 第二次返回 409“该商品已在收藏列表中”，库中仅 1 行 |
| 7 | 操作其他用户收藏 | USER_B 删除 USER_A 的收藏 | ✅ 返回 404，A 的收藏仍在 |
| 8 | 多次设置默认地址 | 建 3 个地址，依次把第 2、第 3 个设为默认 | ✅ 始终只有 1 个 `isDefault=true`，最终为第 3 个 |
| 9 | 删除或修改其他用户地址 | USER_B 对 USER_A 的地址 PUT / DELETE | ✅ 均返回 404，地址内容未变 |
| 10 | 并发添加同一商品唯一索引 | 8 线程同时调用 `cartService.add`（每个数量 1，库存 100） | ✅ 无失败，库中 1 行且数量为 8；断言失败也会在 finally 清理 |

## 2. 补充场景

| 场景 | 真实结果 |
| --- | --- |
| 购物车价格实时读取 | 加购后把商品价格改为 25.00，重新查询购物车返回单价 25.00、小计 50.00、预估合计 50.00 ✅ |
| 加购后商品下架 | 购物车返回 `available=false`、原因含“下架”、`invalidCount=1`、预估金额 0 ✅ |
| 删除默认地址 | 删除后其余地址不自动成为默认；随后手动设为默认成功（证明生成列唯一索引未残留占用）✅ |
| 数据库迁移可重复执行 | `V2__stage5_cart_favorite_address.sql` 连续执行两次成功，三张表与三个唯一索引均存在 ✅ |

## 3. 命令与汇总

```bash
# 后端全量测试
cd backend && PANDAGO_DB_URL=... PANDAGO_TEST_DB_URL=... mvn test

# 后端清理打包
cd backend && mvn clean package

# 前端类型检查 + 构建
cd frontend && npm run build
```

- `mvn test`：**49/49 通过**（上下文 2、分类/商品 8、认证 12、阶段 4 商品 14、阶段 5 用户中心 13），
  失败 0、错误 0。阶段 5 的 13 个用例含上述 10 项指定场景与 3 项补充场景。
- 阶段 2/3/4 的全量测试仍通过，说明本次新增未破坏既有功能。
- 集成测试除并发用例外使用 `@Transactional` 自动回滚；并发用例在 `finally` 中清理自己创建的购物车行与商品，
  避免残留数据污染后续用例。

## 4. 问题与修复记录

1. **并发添加死锁**：初版用“先 insert，捕获唯一键冲突后再条件 update”，8 线程并发时多处死锁。
   修复为一条原子 `INSERT ... ON DUPLICATE KEY UPDATE quantity = IF(quantity + #{n} <= #{stock}, ...)`，
   由唯一索引 + InnoDB 行锁保证串行累加，并保留库存上限条件。修复后 8/8 并发成功、数量正确。
2. **并发用例残留数据污染其他用例**：并发用例不在事务中，断言失败时跳过了清理代码。
   修复为把清理放进 `finally`，无论断言成败都删除测试数据。
3. **MockMvc 中文乱码**：`getContentAsString()` 默认按 ISO-8859-1 解码，导致断言“下架”失败；
   改用 `getContentAsByteArray()` 按 UTF-8 解码，与真实 HTTP 客户端行为一致（仅测试代码问题，接口本身无问题）。
4. 修复过程均未改动业务逻辑的正确性结论；最终全量测试全绿。

## 5. HTTP 端到端联调（真实后端进程）

```
未登录 /cart /favorites /addresses → 401 / 401 / 401
GET /cart 基线 → 200，0 条，预估 0
POST /cart/items ×2（数量 2、3）→ 合并为 1 条，数量 5
PUT quantity=0 → 400；PUT quantity=-1 → 400
PUT selected=false → 预估金额归 0
POST /favorites ×2 → 200 / 409“该商品已在收藏列表中”
GET /favorites → 1 条；GET /favorites/check → true
POST /addresses ×2 → 200；连续两次设默认 → 仅 1 个默认地址
清理后复查 → 购物车 0 / 收藏 0 / 地址 0
```

## 6. 前端渲染检查

- `npm run build`（`vue-tsc --noEmit && vite build`）成功；仅有 Vite 单包体积 >500 kB 的提示。
- 无头 Chrome 访问 `/cart`：未登录被路由守卫重定向到登录页（预期行为），页面渲染正常。
- 无头 Chrome 访问商品详情页：确认“加入购物车”“收藏”“PandaGo 价”“库存”等控件均已渲染。
- 商品卡片新增收藏按钮，详情页可输入数量并加入购物车；购物车、收藏、地址三页均实现加载 / 空 / 错误三态。
