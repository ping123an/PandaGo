-- ============================================================
-- PandaGo 阶段 6 增量迁移：订单主表 / 订单明细
-- 可重复执行：CREATE TABLE IF NOT EXISTS，不 DROP、不清空数据。
-- 设计依据：PRD 5.4 表清单、2.1.10 订单管理、AGENTS 6.4/6.5/6.6。
-- TECH_DESIGN 第四章原文截断且库名为 shop，沿用已确认的 pandago。
-- 订单表不使用 deleted 逻辑删除字段（交易历史按状态流转，不物理删除）。
-- ============================================================

USE `pandago`;

CREATE TABLE IF NOT EXISTS `orders` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no`      VARCHAR(32) NOT NULL COMMENT '订单号，后端生成且唯一',
  `user_id`       BIGINT      NOT NULL COMMENT '下单用户ID（来自登录身份）',
  `merchant_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '商家ID：第一版本单仅含同一商家商品',
  `total_amount`  DECIMAL(10,2) NOT NULL COMMENT '订单总金额（后端按数据库价格计算）',
  `status`        VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '订单状态',
  `remark`        VARCHAR(200) DEFAULT NULL COMMENT '订单备注',
  `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名（快照）',
  `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话（快照）',
  `receiver_province` VARCHAR(50) NOT NULL COMMENT '省（快照）',
  `receiver_city` VARCHAR(50) NOT NULL COMMENT '市（快照）',
  `receiver_district` VARCHAR(50) NOT NULL COMMENT '区（快照）',
  `receiver_detail_address` VARCHAR(200) NOT NULL COMMENT '详细地址（快照）',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_no` (`order_no`),
  KEY `idx_orders_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单主表';

CREATE TABLE IF NOT EXISTS `order_item` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '明细ID',
  `order_id`     BIGINT      NOT NULL COMMENT '订单ID',
  `product_id`   BIGINT      NOT NULL COMMENT '商品ID',
  `product_name` VARCHAR(200) NOT NULL COMMENT '商品名称（快照）',
  `product_image` VARCHAR(255) DEFAULT NULL COMMENT '商品主图（快照）',
  `unit_price`   DECIMAL(10,2) NOT NULL COMMENT '下单单价（快照）',
  `quantity`     INT         NOT NULL COMMENT '购买数量',
  `subtotal`     DECIMAL(10,2) NOT NULL COMMENT '小计（快照）',
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_item_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单明细表';
