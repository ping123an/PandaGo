# 阶段 8 教学重点：角色权限、资源归属、状态流转与关联表

> 本文对应 `merchant` / `shipment` / `order` / `review` / `admin` 模块实现。
> 测试与接口见 `STAGE8_TESTS.md`、`API.md`。
> 本阶段**没有改写阶段 6 的订单创建逻辑，也没有改动阶段 7 的支付/取消逻辑**；
> 新增的是发货、确认收货、评价与管理端能力，全部复用阶段 7 的 `OrderStatusMachine`。

## 1. 角色权限：三道防线，缺一不可

本阶段把权限校验放在三个层次，任何一层都能独立挡住越权请求：

| 层次 | 位置 | 作用 | 典型失败码 |
| --- | --- | --- | --- |
| ① URL 规则 | `SecurityConfig` | `/admin/**` 仅 ADMIN；`/merchant/**` 允许 MERCHANT/ADMIN | 403 |
| ② 方法级注解 | Controller 的 `@PreAuthorize("hasRole('MERCHANT')")` | 即使 URL 配错也挡一层 | 403 |
| ③ 业务层校验 | Service 的 `SecurityUtils.requireRole(...)` | 绕过 Controller 直接调 Service 也挡住 | 403 |

为什么要三层？因为**前端隐藏按钮不是安全边界**。用户可以直接用脚本、Postman、多标签页或改 HTML 绕过前端。
`SecurityUtils.requireRole` 读取的是 JWT 验签后写入的 `GrantedAuthority`，不接受请求体里的 `role` 字段：

```java
SecurityUtils.requireRole(RoleCode.MERCHANT);   // 商家订单/发货
SecurityUtils.requireRole(RoleCode.ADMIN);      // 管理端审核、用户管理、平台订单
```

测试项 1（普通用户访问商家接口）、11（商家自行审核）、12（普通用户访问管理端）验证的就是这三道防线。

## 2. 资源归属：身份决定"能看哪些行"

角色正确 ≠ 能操作这条数据。还要校验**归属**：

- 商家发货：`orders.merchant_id` 必须等于当前登录商家 ID。
- 用户确认收货：`orders.user_id` 必须等于当前登录用户 ID。
- 用户评价：由 `order_item_id` 反查订单，要求 `orders.user_id` = 当前用户。
- 用户查物流：先校验订单归属，再读 `shipment`。

归属校验有两个细节：

1. **身份只从 SecurityContext 取**，绝不信前端传来的 `userId` / `merchantId`。DTO 里根本没有这些字段。
2. **他人数据与不存在数据统一返回 404**，避免通过"403 vs 404"的差异探测某条数据是否存在。
   测试项 5（确认他人订单）和阶段 7 的"支付他人订单"遵循同一原则。

商家订单查询 `MerchantOrderServiceImpl.page` 把 `merchantId` 作为强制 WHERE 条件；
发货的 `requireOwnedOrder` 找到订单但归属不符时返回 403（测试项 2：商家操作其他商家订单）。

## 3. 状态流转：复用状态机 + 条件 UPDATE

阶段 7 已把五态流转集中在 `OrderStatusMachine`，本阶段的发货、确认收货直接复用：

```
PENDING_SHIPMENT --(SHIP 商家发货)--> PENDING_RECEIPT --(CONFIRM_RECEIPT 用户确认)--> COMPLETED
```

每次写操作都是"两步校验 + 一次条件更新"：

```java
// ① 状态机：意图是否合法（同步、可读性好）
OrderStatusMachine.assertTransition(
        order.getStatus(), OrderStatus.PENDING_RECEIPT.getValue(), OrderOperation.SHIP);
// ② 条件 UPDATE：并发下是否仍能变更（数据库行锁判断）
int rows = orderMapper.shipOrder(id, merchantId, FROM, TO);
if (rows != 1) throw new BusinessException(ResultCode.CONFLICT, "订单状态已变化");
```

状态机回答"应不应该做"，条件 UPDATE 回答"并发下能不能安全做成"。两者缺一不可：

- 只有状态机：两个并发发货请求都读到"待发货"，都通过校验，写两条物流记录。
- 只有条件 UPDATE：能防并发，但错误信息不友好，非法操作也要打到数据库才知道。

确认收货的 `confirmReceipt` 在条件 UPDATE 里同时写 `received_at`，只有影响 1 行才写成功；
重复确认第二次影响 0 行返回 409，没有任何副作用（测试项 8）。

## 4. 关联表与唯一索引：评价为什么需要三张表配合

一次"评价"背后涉及四张表的关系：

```
review.order_item_id --唯一--> order_item.id（下单商品快照）
order_item.order_id ------> orders.id（订单主表）
orders.user_id -----------> 当前登录用户（归属）
review.product_id --------> product.id（商品评价列表按它聚合）
```

关键设计：

1. **用 `order_item_id` 而不是 `product_id` 做唯一键**。
   同一商品可以买多次、分多个订单评价；同一订单里的同一商品只能评价一次。
   `uk_review_order_item(order_item_id)` 精确表达这个规则。
2. **前端只传 `orderItemId` + 评分 + 内容**。`userId`、`orderId`、`productId` 全部由后端反查得到，
   前端伪造无效。评分用 `@Min(1) @Max(5)`，内容用 `@Size(min=5,max=500)`（测试项 9 验证未完成订单被拒）。
3. **重复评价两道防线**：先查库给友好 409；并发下唯一索引抛 `DuplicateKeyException`，捕获后仍返回 409
   （测试项 10）。这和阶段 7 的支付幂等是同一套思路。

## 5. 物流信息：只存事实，不编轨迹

`shipment` 表只保存 `company` / `tracking_no` / `shipped_at`（PRD 5.4 表 12、AGENTS 1.2）。
`ShipmentVO` **刻意没有 `tracking` / `routes` 之类字段**，接口和前端都明确说明"不接入真实物流平台、无轨迹数据"。
宁可少展示，也不展示误导用户的伪造进度。

物流写入在发货事务里，且只有在 `shipOrder` 条件 UPDATE 影响 1 行后才写。
`uk_shipment_order(order_id)` 保证一单最多一条物流记录。

## 6. 管理端接口安全与审核留痕

管理端不是"前端不显示菜单"就算保护了。本阶段：

- 所有 `/admin/**` 接口：URL 规则 + `@PreAuthorize` + Service `requireRole` 三重保护。
- 审核操作记录审核人、审核时间和审核意见：
  - 数据库：`product.audit_by` / `product.audit_time`（本次迁移新增），`audit_reason` 复用为意见。
  - 日志：`operation_log` 写入 `module=PRODUCT, operation=AUDIT`，含操作人、对象 ID 与意见。
- 用户状态管理禁止禁用自己，且**不开放角色修改接口**（防止普通用户把自己改成管理员）。
- 平台订单查询是**只读**的，管理员不能绕过状态机直接改订单状态。

如果现有表缺少审核字段怎么办？本次先说明缺口（见本文开头与 `DATABASE.md`），
再用 `V5__stage8_shipment_review_admin.sql` 用"判空后 ALTER"的可重复执行方式补列，
不 DROP、不清空历史数据。

## 7. 事务一致性小结

| 操作 | 事务内写入 | 失败回滚点 |
| --- | --- | --- |
| 商家发货 | 订单条件 UPDATE + shipment 插入 + 操作日志 | 状态抢不到 / 物流插入失败 → 全部回滚 |
| 确认收货 | 订单条件 UPDATE（含 received_at）+ 操作日志 | 重复确认影响 0 行 → 409 无写入 |
| 商品评价 | review 插入 | 未完成 / 重复（唯一索引）→ 无评价产生 |
| 管理员审核 | product 更新（含留痕）+ 操作日志 | 非 ADMIN / 非法结果 → 全部回滚 |

事务都放在独立 Bean（如 `ShipmentTransactionServiceImpl`、`OrderConfirmTransactionServiceImpl`），
由 Spring 代理进入，`@Transactional(rollbackFor = Exception.class)` 确保生效，规避同类自调用陷阱。

## 8. 练习（围绕本阶段真实代码）

**基础题**
1. 为什么商家发货要先查 `orders.merchant_id`，而不是直接按订单 ID 发货？
2. `uk_review_order_item` 为什么用 `order_item_id` 而不是 `product_id`？
3. 前端禁用支付按钮和后端幂等性是什么关系？能否互相替代？

**代码阅读题**
4. 阅读 `ShipmentTransactionServiceImpl.ship`，说明为什么条件 UPDATE 必须在写 shipment 之前。
5. 阅读 `ReviewServiceImpl.create`，列出它做了哪些归属与状态校验，各拒绝什么请求。
6. 阅读 `SecurityConfig` 与 `MerchantOrderController`，说出商家越权请求在哪三层被拦截。

**编码练习**
7. 仿照确认收货，实现"商家仅能查看本商家订单"的分页接口（本阶段已给出参考实现，
   可先自己写再对照 `MerchantOrderServiceImpl.page`）。

**面试题**
8. 什么是幂等性？用本项目的支付或评价举例说明"唯一索引 + 条件更新 + 事务"如何共同保证幂等。
9. 数据库唯一索引和 Service 里的"先查再插"分别解决什么问题？为什么两者都要有？
