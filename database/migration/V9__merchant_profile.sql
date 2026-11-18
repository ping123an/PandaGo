-- ============================================================
-- PandaGo V9 增量迁移：商家店铺资料（工作台 / 商家资料页）
-- 遵循项目现有迁移风格：可重复执行，CREATE TABLE IF NOT EXISTS，
-- 初始化数据用 INSERT ... SELECT ... WHERE NOT EXISTS，不 DROP、不清空。
--
-- 关键设计：本项目"商家 ID 就是用户 ID"（product.merchant_id = user.id），
-- 因此店铺资料表用 user_id 作为唯一键，与 user 表一对一，不重复创建用户表。
-- 店铺状态 / 审核状态由平台控制，普通商家资料接口不允许修改这两个字段。
-- ============================================================

USE `pandago`;

-- 统一连接字符集，避免不同客户端执行迁移时中文常量写入乱码
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `merchant_profile` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '店铺资料ID',
  `user_id`       BIGINT      NOT NULL COMMENT '商家用户ID，与 product.merchant_id 一致',
  `store_name`    VARCHAR(64) NOT NULL COMMENT '店铺名称',
  `logo`          VARCHAR(255) DEFAULT NULL COMMENT '店铺Logo地址',
  `description`   VARCHAR(500) DEFAULT NULL COMMENT '店铺简介',
  `contact_name`  VARCHAR(32)  DEFAULT NULL COMMENT '联系人',
  `contact_phone` VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
  `address`       VARCHAR(200) DEFAULT NULL COMMENT '店铺地址',
  `status`        VARCHAR(16) NOT NULL DEFAULT 'NORMAL' COMMENT '经营状态：NORMAL-正常 / SUSPENDED-暂停',
  `audit_status`  VARCHAR(16) NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态：PENDING/APPROVED/REJECTED',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_profile_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家店铺资料表（与用户一对一）';

-- 为已有 MERCHANT 角色的账号初始化店铺资料；昵称作为默认店铺名。
-- 只填充尚不存在的记录，重复执行不会产生重复数据，也不覆盖已维护的资料。
INSERT INTO `merchant_profile` (`user_id`, `store_name`, `logo`, `description`, `contact_name`, `contact_phone`, `address`, `status`, `audit_status`)
SELECT u.id,
       CONCAT(COALESCE(NULLIF(u.nickname, ''), u.username), ' 的店铺'),
       NULL,
       '该店铺暂未填写简介。',
       u.nickname,
       u.phone,
       NULL,
       'NORMAL',
       'APPROVED'
FROM `user` u
JOIN user_role ur ON ur.user_id = u.id
JOIN role r ON r.id = ur.role_id AND r.code = 'MERCHANT'
WHERE u.deleted = 0
  AND NOT EXISTS (SELECT 1 FROM merchant_profile mp WHERE mp.user_id = u.id);
