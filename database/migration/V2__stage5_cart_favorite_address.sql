-- ============================================================
-- PandaGo 阶段 5 增量迁移：购物车 / 收藏 / 收货地址
-- 可重复执行：全部 CREATE TABLE IF NOT EXISTS，不 DROP、不清空数据。
-- 设计依据：PRD 5.4 表清单、2.1.7/2.1.8/2.1.9 业务规则、AGENTS 第五/六章。
-- TECH_DESIGN 第四章在原文截断（缺少字段明细）且库名为 shop，沿用已确认的 pandago。
-- ============================================================

USE `pandago`;

-- 购物车：user_id + product_id 唯一；不保存价格；物理删除避免旧行占用唯一索引
CREATE TABLE IF NOT EXISTS `cart_item` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '购物车条目ID',
  `user_id`    BIGINT      NOT NULL COMMENT '用户ID（来自登录身份，不接受前端传值）',
  `product_id` BIGINT      NOT NULL COMMENT '商品ID',
  `quantity`   INT         NOT NULL DEFAULT 1 COMMENT '数量，必须为正数且不超过可售库存',
  `selected`   TINYINT     NOT NULL DEFAULT 1 COMMENT '是否勾选：1 是，0 否',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_user_product` (`user_id`, `product_id`),
  KEY `idx_cart_user` (`user_id`, `selected`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='购物车表';

-- 收藏：user_id + product_id 唯一；取消收藏物理删除
CREATE TABLE IF NOT EXISTS `favorite` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id`    BIGINT      NOT NULL COMMENT '用户ID（来自登录身份）',
  `product_id` BIGINT      NOT NULL COMMENT '商品ID',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_user_product` (`user_id`, `product_id`),
  KEY `idx_favorite_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品收藏表';

-- 收货地址：生成列 + 唯一索引保证同一用户最多一个默认地址
CREATE TABLE IF NOT EXISTS `user_address` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '地址ID',
  `user_id`       BIGINT      NOT NULL COMMENT '用户ID（来自登录身份）',
  `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名',
  `phone`         VARCHAR(20) NOT NULL COMMENT '收货人手机号',
  `province`      VARCHAR(50) NOT NULL COMMENT '省份',
  `city`          VARCHAR(50) NOT NULL COMMENT '城市',
  `district`      VARCHAR(50) NOT NULL COMMENT '区县',
  `detail_address` VARCHAR(200) NOT NULL COMMENT '详细地址',
  `is_default`    TINYINT     NOT NULL DEFAULT 0 COMMENT '是否默认：1 是，0 否',
  `default_flag`  BIGINT      GENERATED ALWAYS AS (CASE WHEN is_default = 1 THEN user_id ELSE NULL END) STORED COMMENT '默认地址唯一约束用生成列，不对外暴露',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_address_user_default` (`default_flag`),
  KEY `idx_address_user` (`user_id`, `deleted`, `is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户收货地址表';
