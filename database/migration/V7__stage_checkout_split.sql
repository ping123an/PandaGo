-- ============================================================
-- V7 购物车拆单：订单组编号 + 提交幂等
-- ------------------------------------------------------------
-- 需求：一次购物车提交若包含多个商家的商品，后端按商家拆成多张订单。
-- checkout_group_id 用于：
--   1) 把同一次提交生成的多张订单关联成一个结算批次；
--   2) 与 merchant_id 组成联合唯一索引，作为提交幂等键 —— 同一个结算批次
--      同一个商家只允许生成一张订单，重复点击/重复请求会命中唯一键冲突并整体回滚。
--
-- 兼容性：新列允许 NULL（历史订单保持 NULL），MySQL 唯一索引允许多个 NULL，
-- 因此不会对已有订单造成冲突；新订单由后端写入非空 UUID。
-- 幂等：唯一索引 (checkout_group_id, merchant_id) 保证不重复建单、不重复扣库存。
-- ============================================================

ALTER TABLE `orders`
  ADD COLUMN `checkout_group_id` VARCHAR(64) DEFAULT NULL COMMENT '结算批次号：同一次购物车提交的多张订单共享，配合 merchant_id 做幂等'
  AFTER `merchant_id`;

ALTER TABLE `orders`
  ADD UNIQUE KEY `uk_checkout_group_merchant` (`checkout_group_id`, `merchant_id`);
