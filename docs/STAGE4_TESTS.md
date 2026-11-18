# PandaGo 阶段 4 测试结果

> 执行日期：2026-11-17。所有结果均为本机实际执行输出，未通过的项目会明确列出。

## 1. 自动化测试结果

命令：

```bash
cd backend
PANDAGO_DB_URL='jdbc:mysql://127.0.0.1:3307/pandago?...' \
PANDAGO_DB_USER=root PANDAGO_DB_PASSWORD= \
PANDAGO_TEST_DB_URL='jdbc:mysql://127.0.0.1:3307/pandago?...' \
PANDAGO_TEST_DB_USER=root PANDAGO_TEST_DB_PASSWORD= \
mvn test
```

最终汇总：

```text
Tests run: 12, Failures: 0, Errors: 0  -- AuthIntegrationTests
Tests run:  8, Failures: 0, Errors: 0  -- CatalogIntegrationTests
Tests run:  2, Failures: 0, Errors: 0  -- PandaGoApplicationTests
Tests run: 14, Failures: 0, Errors: 0  -- Stage4ProductFlowTests
Tests run: 36, Failures: 0, Errors: 0
BUILD SUCCESS
```

首次运行时，`invalidImageUrlAndOversizedMetadataAreRejected` 的“无孤立商品”断言失败：测试类外层 `@Transactional` 使 Service 的回滚标记未立即结束外层事务，断言在同一事务中仍看到插入行。给该测试方法加 `@Transactional(propagation = Propagation.NOT_SUPPORTED)` 后重新运行通过。**这是测试隔离写法问题，不是业务回滚失效**；失败请求均返回 400。

## 2. 用户指定的 10 项场景

| # | 场景 | 对应测试 | 结果 |
| --- | --- | --- | --- |
| 1 | 商品分页 | `productPaginationWorks` | 通过 |
| 2 | 关键词搜索 | `keywordSearchFindsMatchingProduct` | 通过 |
| 3 | 分类筛选 | `categoryFilterOnlyReturnsSelectedCategory` | 通过 |
| 4 | 价格区间筛选 | `priceRangeFilterOnlyReturnsProductsInRange` | 通过 |
| 5 | 非法排序字段 | `illegalSortFieldReturns400` | 通过（400） |
| 6 | 下架商品不出现在可购买列表 | `offShelfProductIsNotVisible` | 通过（列表 0、详情 404） |
| 7 | 未审核商品不出现在可购买列表 | `unapprovedProductIsNotVisible` | 通过（列表 0、详情 404） |
| 8 | 商家不能修改他人商品 | `merchantCannotModifyOtherMerchantsProduct` | 通过（403，数据库未变） |
| 9 | 普通用户不能调用商家接口 | `normalUserCannotCallMerchantApi` | 通过（403） |
| 10 | 商品不存在处理 | `nonExistingProductReturns404` | 通过（404） |

## 3. 补充自动化测试

| 场景 | 对应测试 | 结果 |
| --- | --- | --- |
| 商家新增→提交审核→管理员通过→上架→下架完整链路 | `merchantProductCanGoThroughFullAuditAndShelfFlow` | 通过 |
| 主图 + 详情图入库、VO 返回图片数组 | 同上 | 通过（images=2） |
| 未审核上架被拒 | 同上 | 通过（409） |
| 审核驳回强制下架并保存原因 | `rejectingProductForcesOffShelfAndStoresReason` | 通过 |
| 非法图片 URL 与超限声明大小 | `invalidImageUrlAndOversizedMetadataAreRejected` | 通过（400，无孤立商品） |
| 热门商品数量限制 | `hotProductsRespectLimitAndVisibility` | 通过 |

## 4. 构建与联调

| 项目 | 结果 |
| --- | --- |
| `mvn clean package` | 36/36 测试通过，生成 `pandago-backend-0.0.1-SNAPSHOT.jar`，BUILD SUCCESS |
| `npm run build`（含 `vue-tsc --noEmit`） | 成功；仅有 Vite 单包体积 >500 kB 的提示，不影响构建 |
| 数据库迁移 `V1__stage4_product_audit.sql` | 执行两次均成功，`audit_reason` 与 `idx_product_merchant` 存在 |
| 公开 HTTP：`/categories` | 200，6 个一级分类 |
| 公开 HTTP：`/products?sort=price&order=asc` | 200，total=10，返回 5 条 |
| 公开 HTTP：`/products/hot?limit=3` | 200，返回 3 条 |
| 公开 HTTP：非法 sort | 400 |
| 登录：merchant_demo | 200，角色 `MERCHANT` |
| 前端路由 / 组件 HTTP 状态 | `/products`、`/merchant/products`、商家组件均为 200 |
| 无头 Chrome：商品列表页 | 渲染出搜索、价格、排序、商品卡片 |
| 无头 Chrome：首页 | 渲染出分类与热门推荐区域 |
| 无头 Chrome：商品详情页 | 渲染出价格、库存、加入购物车区域 |

**未执行的验证**：本轮没有在浏览器里用演示账号点击完成“登录→商家新增→提交审核→管理员审核→上架”的手工链路；该链路已由 MockMvc 覆盖。需要人工演示时按 `README.md` 的演示账号步骤操作。

## 5. 数据库结果检查

迁移后确认：

```text
audit_reason  varchar(255) YES NULL
idx_product_merchant: (merchant_id, status, audit_status) BTREE
```

初始化数据未被测试污染：分类 6 个一级/7 个二级，商品 10 个已审核上架商品（所有测试事务均回滚）。
