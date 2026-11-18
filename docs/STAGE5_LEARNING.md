# 阶段 5 教学重点：购物车、收藏与收货地址

> 本文对应 `cart` / `favorite` / `address` 三个模块的实现，强调「为什么这样写」，
> 代码示例均来自本项目。测试与接口见 `STAGE5_TESTS.md`、`API.md`。

## 1. 唯一索引：把业务约束交给数据库

三张表各有自己的唯一约束：

| 表 | 唯一索引 | 业务含义 |
| --- | --- | --- |
| `cart_item` | `uk_cart_user_product(user_id, product_id)` | 同一用户同一商品只有一行 |
| `favorite` | `uk_favorite_user_product(user_id, product_id)` | 同一商品不能重复收藏 |
| `user_address` | `uk_address_user_default(default_flag)` | 同一用户最多一个默认地址 |

**为什么不能只在 Java 里先查再写？**
两个并发请求可能同时查到「不存在」，然后双双插入。应用层的检查不是原子操作；唯一索引是
数据库在数据页层面维护的约束，任何绕过 Service 的写入（脚本、后台任务）也无法破坏它。
因此本项目是「Service 提前校验 + 唯一索引兜底」两层，而不是二选一。

**为什么购物车/收藏用物理删除，地址用逻辑删除？**
`cart_item` 和 `favorite` 的唯一键是 `(user_id, product_id)`。如果删除时只置 `deleted=1`，
那一行仍占用唯一键，用户再次添加同一商品就会冲突。它们的业务数据没有审计价值，所以
`DELETE` 物理删除。`user_address` 需要逻辑删除保留历史，但逻辑删除又会让旧默认地址占用
唯一键——解决办法是生成列：

```sql
`default_flag` BIGINT GENERATED ALWAYS AS
  (CASE WHEN is_default = 1 THEN user_id ELSE NULL END) STORED
UNIQUE KEY `uk_address_user_default` (`default_flag`)
```

非默认地址的 `default_flag` 为 `NULL`；MySQL 唯一索引允许多个 `NULL`，于是「非默认」
不冲突，而「同一用户的默认」最多只能有一行。删除默认地址时先把 `is_default` 置 0 再
逻辑删除，索引槽位立即释放，之后仍能重新设为默认（测试已覆盖）。

**一个反例（本项目修复过的真实问题）**：最初购物车用「先 INSERT，捕获
`DuplicateKeyException` 后再条件 UPDATE」，8 线程同加一件商品时出现死锁。原因是多个事务
同时抢插入意向锁与行锁，锁顺序不一致。改为一条原子语句后彻底消失：

```xml
<insert id="upsertQuantity">
  INSERT INTO cart_item (user_id, product_id, quantity, selected, created_at, updated_at)
  VALUES (#{userId}, #{productId}, #{quantity}, 1, NOW(), NOW())
  ON DUPLICATE KEY UPDATE
    quantity = IF(quantity + #{quantity} &lt;= #{stock}, quantity + #{quantity}, quantity),
    updated_at = IF(quantity + #{quantity} &lt;= #{stock}, NOW(), updated_at)
</insert>
```

唯一索引决定走 UPDATE 分支，InnoDB 对已存在的行加锁并串行累加；`IF` 条件保证并发累加也
不会突破库存。受影响行数为 0 表示库存不够，Service 返回 409。

## 2. 事务：一组写操作要么全成、要么全不成

设置默认地址要写两行：清掉旧默认、设置新默认。如果第一条成功、第二条失败，用户就没有
默认地址了，因此必须放在同一个事务里：

```java
@Transactional(rollbackFor = Exception.class)
public void setDefault(Long addressId) {
    Long userId = SecurityUtils.requireCurrentUser().getUserId();
    requireMyAddress(userId, addressId);                  // 先校验归属
    addressMapper.clearDefault(userId);                    // 同一事务：先清旧
    addressMapper.setDefault(userId, addressId);           // 再设新
}
```

要点：
- `@Transactional` 默认只对 `RuntimeException` / `Error` 回滚；显式写
  `rollbackFor = Exception.class` 更稳妥。
- 事务方法内不要吞掉异常；`catch` 后不抛出，Spring 会认为一切正常照常提交。
- 事务范围尽量小：不要把远程调用、文件 IO 放进事务，否则长时间占着数据库连接和行锁。
- 事务和唯一索引是互补的：事务保证多步写入的原子性，唯一索引保证并发下的最终唯一性。
  即使事务写错顺序，数据库也会拒绝产生两个默认地址。

## 3. 关联查询：避免 N+1

购物车要显示商品名、图片、实时价格和库存，这些字段在 `product` 表而不在 `cart_item`。
最直观的写法是遍历购物车、每条查一次商品——N 个条目就是 N+1 次 SQL，数据量大时会成为
性能瓶颈。本项目先批量取回全部商品，再用 Map 在内存里关联：

```java
List<Long> productIds = items.stream().map(CartItem::getProductId).toList();
Map<Long, Product> productMap = new HashMap<>();
for (Product product : productMapper.selectBatchIds(productIds)) {
    productMap.put(product.getId(), product);
}
// 遍历购物车时用 productMap.get(item.getProductId()) 取商品
```

这与 SQL `LEFT JOIN` 的取舍：JOIN 一次查询即可返回全部字段，但结果集嵌套映射较复杂，
且分页/排序在联表后容易出错；「批量查 + 内存 Map」在购物车这种「已限定用户、条目数有限」
的场景更清晰。收藏列表也是同样的做法，并复用 `ProductAvailabilityResolver.describe(product, …)`
重载，避免对已加载的商品再查一次库。

## 4. 用户 ID 从哪来：绝不相信前端传来的 userId

本项目的链路是：

```
登录 → JwtTokenProvider 签发含 userId 的 Token
     → JwtAuthenticationFilter 验签，构造 LoginUserPrincipal 放入 SecurityContext
     → Controller 只声明业务参数
     → Service 调 SecurityUtils.requireCurrentUser().getUserId()
     → Mapper 的 WHERE 条件带上 userId
```

`CartAddDTO` / `FavoriteAddDTO` / `AddressCreateDTO` 中**没有 `userId` 字段**；即使请求体里
塞了 `{"userId": 其他人}`，也会被 Jackson 忽略，Service 用的是 JWT 验签后的身份。
所有按 id 操作的接口都先做归属校验，例如：

```java
private CartItem requireMyItem(Long userId, Long itemId) {
    CartItem item = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
            .eq(CartItem::getId, itemId)
            .eq(CartItem::getUserId, userId));   // 归属条件直接进 WHERE
    if (item == null) {
        throw new BusinessException(ResultCode.NOT_FOUND, "购物车条目不存在");
    }
    return item;
}
```

跨用户访问统一返回 **404 而不是 403**：403 会确认「该资源存在但你没权限」，相当于给攻击者
提供了探测接口；404 让非法 id 与不存在的 id 表现一致。测试第 5、7、9 项验证了这一点。

同样的思路也适用于商家端（阶段 4）：商品归属用 JWT 中的 `merchantId`，而不是前端传参。

## 5. 购物车价格 ≠ 订单成交价

这是电商系统最容易埋隐患的地方：

- **购物车表不存价格字段**。展示时 `price` 实时来自 `product`，商品价格改了，购物车下次查询
  立即显示新价（测试已覆盖：改价 25.00 后小计随之变化）。
- **购物车金额只是 `estimatedTotal`（预估金额）**，只统计「已勾选且可购买」的条目；下架、
  未审核、售罄、库存不足的条目 `available=false`，不计入合计并提示用户重新确认。
- **下单时必须由后端重新读取数据库**：商品是否存在、是否上架、是否审核通过、当前价格、
  当前库存，全部在服务端重新校验，并在 `order_item` 中保存一份成交快照（单价、商品名、
  图片）。阶段 6 会按这个规则实现。
- 前端传来的任何价格、折扣、总金额都只能当作展示数据，**不能作为数据库写入值**。
  否则攻击者改一个 HTTP 请求就能用 1 元买走商品。

购物车还会「提示重新确认」而不是自动删除失效商品：用户加购后商品可能被商家下架或调低
库存，自动删除会让用户困惑；保留条目 + 显式原因 + 不计入预估金额，既保留上下文又不会
误导结算。

## 6. 测试踩坑：MockMvc 的中文编码

`MockMvc` 的 `response.getContentAsString()` 默认按 ISO-8859-1 解码，直接用它读 JSON 会让
中文变成乱码，导致 `assertTrue(reason.contains("下架"))` 失败——但接口本身没有 bug（用
`jsonPath` 断言的其它中文测试全部通过）。修复方式是按字节读取再按 UTF-8 解码：

```java
new String(response.getContentAsByteArray(), StandardCharsets.UTF_8)
```

排查时用「保存日志到文件 + 按 UTF-8 读取 + 打印 repr」比看控制台可靠，因为 Windows 控制台
本身也会把中文显示成乱码。

## 7. 练习

1. 把购物车「重复添加」改成「返回 409 提示用户手动修改数量」，说明会牺牲什么用户体验。
2. 给 `favorite` 加 `deleted` 逻辑删除字段，推演唯一索引会出什么问题，如何改造生成列。
3. 设计订单结算接口：列出必须重新读取的字段与校验顺序，说明为什么订单项要保存价格快照。
4. 用 `EXPLAIN` 查看 `SELECT * FROM cart_item WHERE user_id = ?` 是否命中 `idx_cart_user`，
   并比较加上 `selected` 条件后的执行计划。
