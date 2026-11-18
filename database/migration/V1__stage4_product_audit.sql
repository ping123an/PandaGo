-- ============================================================
-- PandaGo 阶段 4 增量迁移
-- 适用：已经执行过阶段 2 schema.sql 的现有数据库
-- 目标：为商品审核流程增加 audit_reason，并补充商家商品列表索引
-- 可重复执行：通过 information_schema 判断列/索引是否存在
-- ============================================================

USE `pandago`;

-- 1. product.audit_reason
SET @column_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = 'pandago'
    AND TABLE_NAME = 'product'
    AND COLUMN_NAME = 'audit_reason'
);
SET @sql = IF(@column_exists = 0,
  'ALTER TABLE `product` ADD COLUMN `audit_reason` VARCHAR(255) DEFAULT NULL COMMENT ''审核驳回原因'' AFTER `audit_status`',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 商家「我的商品」高频过滤索引
SET @index_exists = (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = 'pandago'
    AND TABLE_NAME = 'product'
    AND INDEX_NAME = 'idx_product_merchant'
);
SET @sql = IF(@index_exists = 0,
  'CREATE INDEX `idx_product_merchant` ON `product` (`merchant_id`, `status`, `audit_status`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
