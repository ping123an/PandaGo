# PandaGo 阶段 4 教学重点

## 1. 本阶段解决的业务问题

阶段 2 已有商品 CRUD，但还不是“可安全浏览、可运营”的商品模块。阶段 4 补齐三件事：

1. 用户端能在统一可见性规则下浏览、搜索、筛选、排序和查看详情。
2. 商家能管理自己的商品，并且不能越过归属边界。
3. 商品主图/详情图以 URL 形式落库，并配合审核状态流转支撑后续下单。

调用链：

```text
浏览器
  -> Vue 页面 / api/product.ts
  -> ProductController / MerchantProductController
  -> ProductService / MerchantProductService
  -> ProductMapper.xml / ProductMapper
  -> MySQL
  -> VO -> JSON -> 页面
```

## 2. 分页查询

`PageResult<T>` 是项目统一分页结构，字段固定为 `list / total / pageNum / pageSize / pages`。

关键流程：

1. Controller 用 `@Min/@Max` 把 `pageNum >= 1`、`1 <= pageSize <= 50` 固定住，防止一次拉全表。
2. Service 构造 MyBatis-Plus `Page<>(pageNum, pageSize)`。
3. Mapper XML 只写业务 WHERE/ORDER BY；分页插件根据 `IPage` 自动追加 `LIMIT` 并生成 COUNT 查询。
4. Service 把 `IPage` 转成项目自己的 `PageResult`，避免把框架类型直接暴露给前端。

**易错点**：前端传的页码不能决定 OFFSET 公式后直接拼 SQL；MyBatis-Plus 的分页插件已处理参数绑定。

## 3. 动态 SQL 与查询条件组合

`ProductMapper.xml` 用 `<if>` 按条件拼接：

```xml
<if test="query.categoryId != null">
  AND p.category_id = #{query.categoryId}
</if>
<if test="query.keyword != null and query.keyword.trim() != ''">
  AND (p.name LIKE CONCAT('%', #{query.keyword}, '%')
       OR p.subtitle LIKE CONCAT('%', #{query.keyword}, '%'))
</if>
```

要点：

- 所有用户输入用 `#{}` 参数绑定，不能出现在 SQL 文本里。
- 条件之间是 AND 组合；关键词内部用 OR 同时匹配商品名和副标题。
- 关键词只接受中文、字母、数字、空格、连字符，长度不超过 50，避免 `%`、`_` 等 LIKE 通配符被滥用。
- Service 校验 `minPrice <= maxPrice`；数据库端两个边界都是 `>=` / `<=`，含边界。

## 4. 排序白名单与 `${}`

`ORDER BY` 的列名和方向不能参数绑定，Mapper XML 中用了 `${query.sortColumn} ${query.sortDirection}`。这是唯一允许使用 `${}` 的位置，而且值必须先经过 `ProductSortResolver`：

```text
sort=default -> p.sort
sort=price   -> p.price
sort=sales   -> p.sales
sort=newest  -> p.created_at
order=asc    -> ASC
order=desc   -> DESC
```

未知值直接返回 400。前端传 `p.price; DROP TABLE product` 不会进入 SQL。

**为什么不用简单替换？** 字符串替换容易遗漏大小写、空格、注释符等变体；白名单是“只允许已知值”，比“过滤危险字符”更可靠。

## 5. 索引与查询条件

阶段 4 增加/沿用的索引：

| 索引 | 支撑查询 |
| --- | --- |
| `idx_product_status(status, audit_status)` | 用户端固定状态过滤 |
| `idx_product_category(category_id, status)` | 分类筛选 |
| `idx_product_price(price)` | 价格排序/区间扫描 |
| `idx_product_sales(sales)` | 热门商品排序 |
| `idx_product_merchant(merchant_id, status, audit_status)` | 商家“我的商品” |

注意：`LIKE '%关键词%'` 前置通配符不能利用 B-tree 索引，当前数据量小可以接受；数据量大后可评估全文索引，而不是盲目给每列加索引。

## 6. DTO / Entity / VO 转换

| 类型 | 用途 | 本阶段例子 |
| --- | --- | --- |
| Entity | 与数据库表一一对应 | `Product`、`ProductImage` |
| DTO | 接收请求 | `ProductQueryDTO`、`ProductCreateDTO`、`ProductAuditDTO` |
| VO | 返回前端 | `ProductListVO`、`ProductDetailVO`、`ProductManagementVO` |

为什么要分开：

- 新增商品不能让前端决定 `merchantId / auditStatus / status / sales`，这些字段不在 DTO 中，Service 从身份和规则生成。
- 详情 VO 不返回 `merchantId` 等内部字段；管理 VO 才返回 `auditReason`。
- `ProductViewAssembler` 统一 Entity → Detail VO，避免不同端映射不一致。

## 7. 资源归属校验

商家接口有两层校验：

1. URL 规则 + `@PreAuthorize("hasRole('MERCHANT')")` 拒绝普通用户。
2. Service 内 `SecurityUtils.requireRole(MERCHANT)` 和 `currentMerchantId()` 再校验一次，并从 SecurityContext 取 id。

归属判定：

```java
if (product.getMerchantId() == null || !merchantId.equals(product.getMerchantId())) {
    throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己创建的商品");
}
```

关键点：**角色校验不等于归属校验**。是商家不等于能改任意商品；`merchantId` 只能来自 JWT 验签后的 SecurityContext，不能来自请求参数。

## 8. 事务与图片写入

`create / update / submitForAudit / audit` 都在 Service 方法上加 `@Transactional(rollbackFor = Exception.class)`。图片写入器先校验所有 URL，再删除旧图、插入新图；任一异常抛出后，商品主表和图片表一起回滚。

本阶段没有对象存储，`sizeBytes` 只是请求声明值，服务端没有读取文件流，因此**不能宣称已经实现生产级文件上传或真实文件字节校验**。

## 9. 练习（先自己尝试，再看代码）

### 基础知识题

1. 为什么分页参数要限制 `pageSize` 上限？
2. `#{}` 和 `${}` 的区别是什么？为什么排序字段特别容易出错？
3. `idx_product_merchant(merchant_id, status, audit_status)` 为什么把 `merchant_id` 放第一列？

### 代码阅读题

1. 在 `ProductServiceImpl.page` 中找出“用户输入进入 SQL 前”经过的所有校验点。
2. 在 `MerchantProductServiceImpl` 中找出商家 id 的来源，并说明为什么不是 DTO 字段。
3. `ProductImageWriter.replaceImages` 为什么先校验全部图片再删除旧记录？

### 独立编码练习

给 `GET /products` 增加一个 `sort=oldest` 选项：只改 `ProductSortResolver` 的白名单映射，不碰 Mapper XML 的 `${}`，并写一个测试验证非法值仍返回 400。

### 初级后端面试题

1. 实现一个商品搜索接口时，如何同时保证分页正确、SQL 注入防护和排序安全？
2. Spring Service 中“角色校验”和“资源归属校验”分别解决什么问题？为什么两者都要做？

## 10. 阶段总结

- Java / Spring Boot：`@Valid`、`@ConfigurationProperties`、`@Transactional`、`@PreAuthorize`、自定义 `BusinessException`。
- MyBatis-Plus：`IPage` 分页、Mapper XML 动态 SQL、逻辑删除自动条件、参数绑定与白名单。
- MySQL：组合索引、含边界价格过滤、`LIKE` 前置通配符的索引限制。
- 前端：Pinia 登录态、Axios Token 注入、Element Plus 表格/表单/分页、加载/空/错误三态。
- 面试表述：可以讲清“商品列表只读可见商品、商家写入必须同时校验角色和归属、图片先用 URL 落库并明确未实现上传”这条完整链路。
