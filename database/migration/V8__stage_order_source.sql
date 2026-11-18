-- V8：订单来源字段（CART=购物车结算 / DIRECT=立即购买）
-- 用于区分两种购买流程，不改变既有订单状态机与库存策略。
-- 历史订单统一按 CART 处理，兼容旧数据。

ALTER TABLE `orders`
  ADD COLUMN `source` VARCHAR(16) NOT NULL DEFAULT 'CART' COMMENT '订单来源：CART-购物车结算，DIRECT-立即购买' AFTER `checkout_group_id`;
