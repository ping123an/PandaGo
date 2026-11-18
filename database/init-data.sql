-- ============================================================
-- PandaGo 阶段 2 初始化数据
--
-- 可重复执行：全部使用 INSERT IGNORE 或 INSERT IGNORE ... SELECT，
-- 依靠唯一键跳过已存在记录；不会 DELETE、TRUNCATE 或覆盖用户修改过的数据。
-- 本脚本不写入任何用户账号和密码。
--   原因：BCrypt 哈希需绑定阶段 3 的注册/登录实现生成；
--         测试账号将在阶段 3 用 BCryptPasswordEncoder 创建，仅用于本地开发。
-- 商品图片为项目内占位 SVG（frontend/public/images/panda-product.svg），
-- 不引用任何第三方品牌素材。
-- ============================================================

USE `pandago`;

-- ------------------------------------------------------------
-- 角色：受控枚举，禁止普通用户通过注册自选
-- ------------------------------------------------------------
INSERT IGNORE INTO `role` (`code`, `name`, `description`) VALUES
  ('USER',     '普通用户', '浏览商品、购物车、下单与评价'),
  ('MERCHANT', '商家',     '管理自有商品与订单发货'),
  ('ADMIN',    '管理员',   '平台用户、分类、商品审核与订单管理');

-- ------------------------------------------------------------
-- 一级分类（parent_id = 0）
-- ------------------------------------------------------------
INSERT IGNORE INTO `category` (`parent_id`, `name`, `sort`, `status`) VALUES
  (0, '手机数码', 1, 1),
  (0, '家用电器', 2, 1),
  (0, '服装鞋包', 3, 1),
  (0, '食品生鲜', 4, 1),
  (0, '家居生活', 5, 1),
  (0, '美妆护肤', 6, 1);

-- ------------------------------------------------------------
-- 二级分类（示例：每类 1~2 个，保持"少量"）
-- ------------------------------------------------------------
INSERT IGNORE INTO `category` (`parent_id`, `name`, `sort`, `status`)
SELECT c.id, s.name, s.sort, 1
FROM `category` c
JOIN (
  SELECT '手机数码' AS parent_name, '手机' AS name, 1 AS sort
  UNION ALL SELECT '手机数码', '数码配件', 2
  UNION ALL SELECT '家用电器', '生活电器', 1
  UNION ALL SELECT '服装鞋包', '女装', 1
  UNION ALL SELECT '食品生鲜', '休闲零食', 1
  UNION ALL SELECT '家居生活', '家居用品', 1
  UNION ALL SELECT '美妆护肤', '护肤品', 1
) s ON s.parent_name = c.name AND c.parent_id = 0;

-- ------------------------------------------------------------
-- 商品：仅展示审核通过且上架的商品（AGENTS 6.2）
-- 价格使用 DECIMAL 字面量；merchant_id = 0 表示平台演示商品
-- ------------------------------------------------------------
INSERT IGNORE INTO `product`
  (`category_id`, `merchant_id`, `name`, `subtitle`, `main_image`, `price`, `stock`, `sales`, `status`, `audit_status`, `description`, `sort`)
SELECT c.id, 0, p.name, p.subtitle, '/images/panda-product.svg', p.price, p.stock, p.sales, 'ON_SALE', 'APPROVED', p.description, p.sort
FROM `category` c
JOIN (
  SELECT '手机数码' AS category_name, 'PandaGo 无线降噪耳机 Pro' AS name, '40小时续航 · 主动降噪' AS subtitle, 299.00 AS price, 60 AS stock, 128 AS sales, '轻盈佩戴，通勤与办公都适合。' AS description, 10 AS sort
  UNION ALL SELECT '手机数码', '竹影 智能手机 X1', '6.7英寸全面屏 · 大电池', 1999.00, 25, 86, '日常拍照与娱乐的高性价比选择。', 9
  UNION ALL SELECT '家用电器', '熊猫家 迷你冷藏冰箱', '一级能效 · 静音运行', 899.00, 30, 42, '小户型与办公室的冷藏好帮手。', 8
  UNION ALL SELECT '家用电器', '清净 空气净化器', '三重过滤 · 低噪设计', 1299.00, 18, 35, '适合卧室与客厅的空气净化设备。', 7
  UNION ALL SELECT '服装鞋包', '熊猫印花 纯棉短袖 T 恤', '亲肤透气 · 日常百搭', 99.00, 200, 210, '原创熊猫图案，舒适纯棉面料。', 6
  UNION ALL SELECT '服装鞋包', '轻旅 多功能双肩背包', '大容量 · 防泼水', 159.00, 120, 93, '通勤短途都适用的轻量背包。', 5
  UNION ALL SELECT '食品生鲜', '竹林 每日坚果礼盒', '独立包装 · 每日一袋', 69.00, 300, 156, '多种坚果果干组合，方便携带。', 4
  UNION ALL SELECT '食品生鲜', '山泉 天然矿泉水 12 瓶装', '550ml × 12', 35.00, 500, 320, '日常饮水囤货装。', 3
  UNION ALL SELECT '家居生活', '温馨 北欧简约台灯', '护眼光 · 三档调色', 129.00, 80, 67, '阅读与办公的柔和光源。', 2
  UNION ALL SELECT '美妆护肤', '柔光 保湿精华液', '轻盈质地 · 补水保湿', 239.00, 90, 74, '日常护肤的基础精华。', 1
) p ON p.category_name = c.name AND c.parent_id = 0;

-- ------------------------------------------------------------
-- 审核演示数据：待审核 2 条 + 已驳回 1 条
-- 让管理端审核页默认「待审核」有商品可处理；已驳回商品用于查看审核记录。
-- 唯一键 (category_id, name) + INSERT IGNORE 保证重复执行安全。
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 商品图片（一对多示例：每个商品一张图）
-- ------------------------------------------------------------
INSERT IGNORE INTO `product_image` (`product_id`, `url`, `sort`)
SELECT p.id, '/images/panda-product.svg', 1
FROM `product` p
WHERE p.deleted = 0;
