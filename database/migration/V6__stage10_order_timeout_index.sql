-- ============================================================
-- PandaGo 阶段 10 迁移：超时订单扫描优化索引
-- 目标：SELECT id FROM orders WHERE status=? AND created_at<=?
--       ORDER BY created_at ASC, id ASC LIMIT ?
-- 该查询原先无 status 前导索引（idx_orders_merchant 前导列是 merchant_id），
-- 数据量大时全表扫描；新增 (status, created_at, id) 覆盖索引。
-- 幂等：用 information_schema 判重，可重复执行。
-- ============================================================

SET @idx_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'orders'
    AND index_name = 'idx_orders_status_created'
);
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE `orders` ADD KEY `idx_orders_status_created` (`status`, `created_at`, `id`)',
  'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
