-- ============================================================
-- PandaGo 阶段 8 增量迁移：商家发货 / 确认收货 / 商品评价 / 管理端
-- 可重复执行：CREATE TABLE IF NOT EXISTS；加列用 information_schema + 动态 SQL 判空，
-- 不 DROP、不清空、不覆盖历史数据。
-- 设计依据：PRD 5.4 表清单（shipment/review/operation_log）、2.1.12 评价、
-- 2.2.3 商家订单、2.3 管理端、AGENTS 6.6/6.8。
-- TECH_DESIGN 第四章原文截断且库名为 shop，沿用已确认的 pandago。
-- 说明：product 已有 audit_reason 作为审核意见，本阶段只补审核人/审核时间两列；
--       orders 补 received_at 记录确认收货时间，不改动阶段 6/7 既有字段。
-- ============================================================

USE `pandago`;

-- ------------------------------------------------------------
-- 1. product 审核留痕：审核人 ID + 审核时间（审核意见复用 audit_reason）
-- ------------------------------------------------------------
SET @pandago_col = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'pandago' AND TABLE_NAME = 'product' AND COLUMN_NAME = 'audit_by');
SET @pandago_sql = IF(@pandago_col = 0,
  'ALTER TABLE `product` ADD COLUMN `audit_by` BIGINT DEFAULT NULL COMMENT ''审核人用户ID'' AFTER `audit_reason`',
  'SELECT 1');
PREPARE pandago_stmt FROM @pandago_sql; EXECUTE pandago_stmt; DEALLOCATE PREPARE pandago_stmt;

SET @pandago_col = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'pandago' AND TABLE_NAME = 'product' AND COLUMN_NAME = 'audit_time');
SET @pandago_sql = IF(@pandago_col = 0,
  'ALTER TABLE `product` ADD COLUMN `audit_time` DATETIME DEFAULT NULL COMMENT ''审核时间'' AFTER `audit_by`',
  'SELECT 1');
PREPARE pandago_stmt FROM @pandago_sql; EXECUTE pandago_stmt; DEALLOCATE PREPARE pandago_stmt;

-- ------------------------------------------------------------
-- 2. orders 确认收货时间
-- ------------------------------------------------------------
SET @pandago_col = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'pandago' AND TABLE_NAME = 'orders' AND COLUMN_NAME = 'received_at');
SET @pandago_sql = IF(@pandago_col = 0,
  'ALTER TABLE `orders` ADD COLUMN `received_at` DATETIME DEFAULT NULL COMMENT ''确认收货时间（服务端时间）'' AFTER `updated_at`',
  'SELECT 1');
PREPARE pandago_stmt FROM @pandago_sql; EXECUTE pandago_stmt; DEALLOCATE PREPARE pandago_stmt;

-- ------------------------------------------------------------
-- 3. shipment 物流信息表（PRD 5.4 表 12）
--    只保存物流公司与单号，不保存也不展示伪造轨迹（AGENTS 1.2）。
--    order_id 唯一：一单只发货一次，条件状态更新失败时不会写入物流。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `shipment` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '物流记录ID',
  `order_id`     BIGINT      NOT NULL COMMENT '订单ID',
  `order_no`     VARCHAR(32) NOT NULL COMMENT '订单号快照',
  `merchant_id`  BIGINT      NOT NULL COMMENT '发货商家ID（来自登录身份）',
  `company`      VARCHAR(50) NOT NULL COMMENT '物流公司',
  `tracking_no`  VARCHAR(64) NOT NULL COMMENT '物流单号',
  `shipped_at`   DATETIME    NOT NULL COMMENT '发货时间（服务端时间）',
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipment_order` (`order_id`),
  KEY `idx_shipment_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单物流信息表（仅公司/单号，无真实轨迹）';

-- ------------------------------------------------------------
-- 4. review 商品评价表（PRD 5.4 表 13）
--    order_item_id 唯一：同一订单商品只能评价一次；
--    订单归属与完成状态由后端校验，数据库唯一索引兜住并发重复提交。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `review` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `user_id`       BIGINT       NOT NULL COMMENT '评价用户ID（来自登录身份）',
  `order_id`      BIGINT       NOT NULL COMMENT '订单ID',
  `order_item_id` BIGINT       NOT NULL COMMENT '订单明细ID，一单一商品一条评价',
  `product_id`    BIGINT       NOT NULL COMMENT '商品ID',
  `product_name`  VARCHAR(200) NOT NULL COMMENT '商品名称快照，评价列表展示用',
  `rating`        TINYINT      NOT NULL COMMENT '评分：1~5',
  `content`       VARCHAR(500) NOT NULL COMMENT '评价内容',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_order_item` (`order_item_id`),
  KEY `idx_review_product` (`product_id`, `created_at`),
  KEY `idx_review_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品评价表';

-- ------------------------------------------------------------
-- 5. operation_log 操作日志表（PRD 5.4 表 15）
--    记录管理端与关键业务操作的操作人、动作、对象与意见。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `operation_log` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `operator_id`   BIGINT      NOT NULL COMMENT '操作人用户ID（来自登录身份）',
  `operator_name` VARCHAR(50) DEFAULT NULL COMMENT '操作人用户名快照',
  `module`        VARCHAR(30) NOT NULL COMMENT '模块：PRODUCT/CATEGORY/USER/ORDER/SHIPMENT',
  `operation`     VARCHAR(50) NOT NULL COMMENT '操作：AUDIT/SHIP/STATUS/...',
  `target_type`   VARCHAR(30) DEFAULT NULL COMMENT '对象类型：PRODUCT/ORDER/USER/CATEGORY',
  `target_id`     BIGINT      DEFAULT NULL COMMENT '对象ID',
  `detail`        VARCHAR(500) DEFAULT NULL COMMENT '操作说明或审核意见',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间（服务端时间）',
  PRIMARY KEY (`id`),
  KEY `idx_operation_log_operator` (`operator_id`, `created_at`),
  KEY `idx_operation_log_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台操作日志表';
