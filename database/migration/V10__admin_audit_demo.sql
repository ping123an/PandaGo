-- ============================================================
-- V10 平台商品审核演示数据
-- 目的：初始化/升级后审核页默认「待审核」有真实数据可验，
--       同时提供一条已驳回商品用于查看审核记录。
-- 幂等：依赖 product 唯一键 (category_id, name)，重复执行不会插入重复行。
-- 使用：mysql --default-character-set=utf8mb4 < database/migration/V10__admin_audit_demo.sql
-- ============================================================
SET NAMES utf8mb4;

-- 1) 两条待审核商品：OFF_SALE + PENDING，与真实商家提交后的初始状态一致
INSERT IGNORE INTO `product`
  (`category_id`, `merchant_id`, `name`, `subtitle`, `main_image`, `price`, `stock`, `sales`, `status`, `audit_status`, `description`, `sort`)
SELECT c.id, 0, p.name, p.subtitle, '/images/panda-product.svg', p.price, p.stock, 0, 'OFF_SALE', 'PENDING', p.description, p.sort
FROM `category` c
JOIN (
  SELECT '手机数码' AS category_name, 'PandaGo 便携磁吸充电宝' AS name, '10000mAh · 20W 快充' AS subtitle,
         129.00 AS price, 150 AS stock, '出差通勤随手补电，兼容主流快充协议。' AS description, 11 AS sort
  UNION ALL
  SELECT '家居生活', '竹影 香薰加湿器', '静音加湿 · 氛围夜灯', 189.00, 80, '卧室与办公室适用的轻量加湿器。', 12
) p ON p.category_name = c.name AND c.parent_id = 0;

-- 2) 一条已驳回商品：OFF_SALE + REJECTED，带审核人/时间/原因，便于验证审核记录只读展示
INSERT IGNORE INTO `product`
  (`category_id`, `merchant_id`, `name`, `subtitle`, `main_image`, `price`, `stock`, `sales`,
   `status`, `audit_status`, `audit_reason`, `audit_by`, `audit_time`, `description`, `sort`)
SELECT c.id, 0, '轻旅 折叠旅行收纳包', '多仓分层 · 防水面料', '/images/panda-product.svg', 79.00, 60, 0,
       'OFF_SALE', 'REJECTED',
       '商品图片与描述不够清晰，请补充实物图和详细参数后重新提交审核。',
       (SELECT u.id FROM `user` u WHERE u.username = 'admin_demo' AND u.deleted = 0 LIMIT 1),
       NOW(), '多仓分层设计，适合短途旅行收纳。', 13
FROM `category` c
WHERE c.name = '服装鞋包' AND c.parent_id = 0;

-- 3) 演示商品主图（唯一键 product_id + url，重复执行安全）
INSERT IGNORE INTO `product_image` (`product_id`, `url`, `sort`)
SELECT p.id, '/images/panda-product.svg', 1
FROM `product` p
WHERE p.deleted = 0
  AND p.name IN ('PandaGo 便携磁吸充电宝', '竹影 香薰加湿器', '轻旅 折叠旅行收纳包');
