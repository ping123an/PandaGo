-- ============================================================
-- PandaGo 阶段 4 本地演示账号（仅用于开发/教学，不要在生产环境执行）
-- 统一初始密码：Panda@12345
-- 密码哈希由 BCryptPasswordEncoder 生成；重复执行不会覆盖已有账号或角色
-- ============================================================

USE `pandago`;

INSERT IGNORE INTO `user` (`username`, `password_hash`, `nickname`, `status`)
VALUES
  ('merchant_demo', '$2a$10$xShsB502scE29M8A5pwlMuCX.n2kBaNL7pOM8ohiJ7sGo.R0X10XW', '熊猫商家', 1),
  ('user_demo',     '$2a$10$xShsB502scE29M8A5pwlMuCX.n2kBaNL7pOM8ohiJ7sGo.R0X10XW', '熊猫买家', 1),
  ('admin_demo',    '$2a$10$xShsB502scE29M8A5pwlMuCX.n2kBaNL7pOM8ohiJ7sGo.R0X10XW', '平台管理员', 1);

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM `user` u
JOIN `role` r ON r.code = 'MERCHANT'
WHERE u.username = 'merchant_demo';

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM `user` u
JOIN `role` r ON r.code = 'USER'
WHERE u.username = 'user_demo';

INSERT IGNORE INTO `user_role` (`user_id`, `role_id`)
SELECT u.id, r.id
FROM `user` u
JOIN `role` r ON r.code = 'ADMIN'
WHERE u.username = 'admin_demo';
