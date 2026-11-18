-- ============================================================
-- PandaGo 阶段 2 建表脚本
-- 数据库：pandago  字符集：utf8mb4  排序规则：utf8mb4_0900_ai_ci
-- 引擎：InnoDB
--
-- 可重复执行：全部使用 CREATE TABLE IF NOT EXISTS，不 DROP、不清空数据。
-- 字段设计依据：PRD 5.4（表清单/主键/金额/库存规范）、AGENTS 第五/六章、
-- TECH_DESIGN 第四章（该章在原文中缺失字段明细，补充记录见 docs/DATABASE.md）。
-- ============================================================

CREATE DATABASE IF NOT EXISTS `pandago`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `pandago`;

-- ------------------------------------------------------------
-- 1. user 用户表
--    角色通过 user_role 关联，不在 user 表内硬编码角色字段。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username`     VARCHAR(50)  NOT NULL COMMENT '用户名（登录账号）',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希，禁止明文',
  `nickname`     VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  `avatar`       VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
  `phone`        VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. role 角色表
--    code 使用受控枚举：USER / MERCHANT / ADMIN
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `role` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `code`        VARCHAR(30) NOT NULL COMMENT '角色编码：USER/MERCHANT/ADMIN',
  `name`        VARCHAR(50) NOT NULL COMMENT '角色名称',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '角色说明',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

-- ------------------------------------------------------------
-- 3. user_role 用户角色关联表（多对多）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_role` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT   NOT NULL COMMENT '用户ID',
  `role_id`    BIGINT   NOT NULL COMMENT '角色ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role_pair` (`user_id`, `role_id`),
  KEY `idx_user_role_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关联表';

-- ------------------------------------------------------------
-- 4. category 商品分类表（两级：parent_id=0 为一级）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `category` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '分类ID',
  `parent_id`  BIGINT      NOT NULL DEFAULT 0 COMMENT '父分类ID，0 表示一级分类',
  `name`       VARCHAR(50) NOT NULL COMMENT '分类名称',
  `sort`       INT         NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
  `icon`       VARCHAR(100) DEFAULT NULL COMMENT '图标地址（可选）',
  `status`     TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_category_name_parent` (`parent_id`, `name`),
  KEY `idx_category_parent` (`parent_id`, `status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品分类表';

-- ------------------------------------------------------------
-- 5. product 商品表
--    status：ON_SALE / OFF_SALE；audit_status：PENDING / APPROVED / REJECTED
--    merchant_id：阶段 2 尚无商家表，初始化数据用 0 表示平台演示商品，
--                 阶段 4/8 接入商家后由后端按登录身份赋值，禁止信任前端传值。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `product` (
  `id`           BIGINT         NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `category_id`  BIGINT         NOT NULL COMMENT '所属分类ID',
  `merchant_id`  BIGINT         NOT NULL DEFAULT 0 COMMENT '商家ID，0 为平台演示商品',
  `name`         VARCHAR(150)   NOT NULL COMMENT '商品名称',
  `subtitle`     VARCHAR(255)   DEFAULT NULL COMMENT '商品简短描述',
  `main_image`   VARCHAR(255)   NOT NULL COMMENT '商品主图地址',
  `price`        DECIMAL(10, 2) NOT NULL COMMENT '商品价格，禁止浮点类型',
  `stock`        INT            NOT NULL DEFAULT 0 COMMENT '可售库存',
  `sales`        INT            NOT NULL DEFAULT 0 COMMENT '销量',
  `status`       VARCHAR(20)    NOT NULL DEFAULT 'OFF_SALE' COMMENT '上架状态：ON_SALE/OFF_SALE',
  `audit_status` VARCHAR(20)    NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING/APPROVED/REJECTED',
  `audit_reason` VARCHAR(255)   DEFAULT NULL COMMENT '审核意见/驳回原因',
  `audit_by`     BIGINT         DEFAULT NULL COMMENT '审核人用户ID（阶段8）',
  `audit_time`   DATETIME       DEFAULT NULL COMMENT '审核时间（阶段8）',
  `description`  TEXT           DEFAULT NULL COMMENT '商品详情描述',
  `sort`         INT            NOT NULL DEFAULT 0 COMMENT '推荐排序值',
  `created_at`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT        NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 否，1 是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_category_name` (`category_id`, `name`),
  KEY `idx_product_category` (`category_id`, `status`),
  KEY `idx_product_status` (`status`, `audit_status`),
  KEY `idx_product_price` (`price`),
  KEY `idx_product_sales` (`sales`),
  KEY `idx_product_merchant` (`merchant_id`, `status`, `audit_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品表';

-- ------------------------------------------------------------
-- 6. product_image 商品图片表（一对多）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `product_image` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '图片ID',
  `product_id` BIGINT       NOT NULL COMMENT '商品ID',
  `url`        VARCHAR(255) NOT NULL COMMENT '图片地址',
  `sort`       INT          NOT NULL DEFAULT 0 COMMENT '展示顺序',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_image_pair` (`product_id`, `url`),
  KEY `idx_product_image_product` (`product_id`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品图片表';

-- ------------------------------------------------------------
-- 7. cart_item 购物车表（阶段 5）
--    同一用户同一商品只保留一条：uk_cart_user_product 唯一索引。
--    不存价格：展示价格实时从 product 读取，成交价在下单时再按数据库计算。
--    删除购物车条目采用物理删除，避免逻辑删除的旧行占用唯一索引。
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 8. favorite 收藏表（阶段 5）
--    user_id + product_id 唯一索引防重复收藏；取消收藏物理删除。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `favorite` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '收藏ID',
  `user_id`    BIGINT      NOT NULL COMMENT '用户ID（来自登录身份）',
  `product_id` BIGINT      NOT NULL COMMENT '商品ID',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_favorite_user_product` (`user_id`, `product_id`),
  KEY `idx_favorite_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品收藏表';

-- ------------------------------------------------------------
-- 9. user_address 收货地址表（阶段 5）
--    is_default：1 默认，0 非默认；一个用户最多一个默认地址。
--    uk_address_user_default 使用生成列，把"默认用户"映射为唯一值：
--    仅 is_default=1 时 default_flag=user_id，否则为 NULL（MySQL 唯一索引允许多个 NULL），
--    从而在数据库层也阻止同一用户出现两条默认地址。
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 10. orders 订单主表（阶段 6）
--     交易记录不物理删除；通过 status 管理生命周期。
--     收货信息与订单金额均为下单时刻快照，不随用户资料/商品价格变化。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `orders` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '订单ID',
  `order_no`      VARCHAR(32) NOT NULL COMMENT '订单号，后端生成且唯一',
  `user_id`       BIGINT      NOT NULL COMMENT '下单用户ID（来自登录身份）',
  `merchant_id`   BIGINT      NOT NULL DEFAULT 0 COMMENT '商家ID：拆单后每张订单仅含同一商家商品',
  `checkout_group_id` VARCHAR(64) DEFAULT NULL COMMENT '结算批次号：同一次购物车提交的多张订单共享，配合 merchant_id 做幂等（V7）',
  `source`        VARCHAR(16) NOT NULL DEFAULT 'CART' COMMENT '订单来源：CART-购物车结算，DIRECT-立即购买（V8）',
  `total_amount`  DECIMAL(10,2) NOT NULL COMMENT '订单总金额（后端按数据库价格计算）',
  `status`        VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '订单状态：PENDING_PAYMENT/PENDING_SHIPMENT/PENDING_RECEIPT/COMPLETED/CANCELLED',
  `remark`        VARCHAR(200) DEFAULT NULL COMMENT '订单备注',
  `receiver_name` VARCHAR(50) NOT NULL COMMENT '收货人姓名（快照）',
  `receiver_phone` VARCHAR(20) NOT NULL COMMENT '收货人电话（快照）',
  `receiver_province` VARCHAR(50) NOT NULL COMMENT '省（快照）',
  `receiver_city` VARCHAR(50) NOT NULL COMMENT '市（快照）',
  `receiver_district` VARCHAR(50) NOT NULL COMMENT '区（快照）',
  `receiver_detail_address` VARCHAR(200) NOT NULL COMMENT '详细地址（快照）',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `received_at`   DATETIME    DEFAULT NULL COMMENT '确认收货时间（阶段8）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_orders_no` (`order_no`),
  UNIQUE KEY `uk_checkout_group_merchant` (`checkout_group_id`, `merchant_id`),
  KEY `idx_orders_user` (`user_id`, `created_at`),
  KEY `idx_orders_merchant` (`merchant_id`, `status`, `created_at`),
  -- 阶段10：超时订单扫描 WHERE status=? AND created_at<=? ORDER BY created_at,id 的覆盖索引，避免全表扫描
  KEY `idx_orders_status_created` (`status`, `created_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单主表';

-- ------------------------------------------------------------
-- 11. order_item 订单明细表（阶段 6）
--     商品名称/图片/单价/数量/小计均为下单时刻快照。
-- ------------------------------------------------------------
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

-- ------------------------------------------------------------
-- 12. payment_record 模拟支付记录表（阶段 7）
--     payment_no 唯一索引保证流水号唯一；order_id 唯一索引保证一单一支付。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `payment_record` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '支付记录ID',
  `payment_no`     VARCHAR(64)   NOT NULL COMMENT '支付流水号，后端生成且唯一',
  `order_id`       BIGINT        NOT NULL COMMENT '订单ID',
  `order_no`       VARCHAR(32)   NOT NULL COMMENT '订单号快照',
  `user_id`        BIGINT        NOT NULL COMMENT '支付用户ID（来自登录身份）',
  `pay_amount`     DECIMAL(10,2) NOT NULL COMMENT '实付金额，只取 orders.total_amount',
  `payment_method` VARCHAR(20)   NOT NULL DEFAULT 'MOCK' COMMENT '支付方式：本项目固定 MOCK',
  `status`         VARCHAR(20)   NOT NULL DEFAULT 'SUCCESS' COMMENT '支付状态：SUCCESS',
  `paid_at`        DATETIME      NOT NULL COMMENT '支付成功时间（服务端时间）',
  `created_at`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  UNIQUE KEY `uk_payment_order` (`order_id`),
  KEY `idx_payment_order_no` (`order_no`),
  KEY `idx_payment_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模拟支付记录表';

-- ------------------------------------------------------------
-- 13. shipment 物流信息表（阶段 8）
--     一单只发货一次：uk_shipment_order；只存公司与单号，不展示伪造轨迹。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `shipment` (
  `id`           BIGINT      NOT NULL AUTO_INCREMENT COMMENT '物流记录ID',
  `order_id`     BIGINT      NOT NULL COMMENT '订单ID',
  `order_no`     VARCHAR(32) NOT NULL COMMENT '订单号快照',
  `merchant_id`  BIGINT      NOT NULL COMMENT '发货商家ID',
  `company`      VARCHAR(50) NOT NULL COMMENT '物流公司',
  `tracking_no`  VARCHAR(64) NOT NULL COMMENT '物流单号',
  `shipped_at`   DATETIME    NOT NULL COMMENT '发货时间（服务端时间）',
  `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shipment_order` (`order_id`),
  KEY `idx_shipment_order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单物流信息表（仅公司/单号，无真实轨迹）';

-- ------------------------------------------------------------
-- 14. review 商品评价表（阶段 8）
--     uk_review_order_item：同一订单商品只评价一次。
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `review` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评价ID',
  `user_id`       BIGINT       NOT NULL COMMENT '评价用户ID',
  `order_id`      BIGINT       NOT NULL COMMENT '订单ID',
  `order_item_id` BIGINT       NOT NULL COMMENT '订单明细ID，一单一商品一条评价',
  `product_id`    BIGINT       NOT NULL COMMENT '商品ID',
  `product_name`  VARCHAR(200) NOT NULL COMMENT '商品名称快照',
  `rating`        TINYINT      NOT NULL COMMENT '评分：1~5',
  `content`       VARCHAR(500) NOT NULL COMMENT '评价内容',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_order_item` (`order_item_id`),
  KEY `idx_review_product` (`product_id`, `created_at`),
  KEY `idx_review_user` (`user_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商品评价表';

-- ------------------------------------------------------------
-- 15. operation_log 操作日志表（阶段 8）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `operation_log` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `operator_id`   BIGINT      NOT NULL COMMENT '操作人用户ID',
  `operator_name` VARCHAR(50) DEFAULT NULL COMMENT '操作人用户名快照',
  `module`        VARCHAR(30) NOT NULL COMMENT '模块',
  `operation`     VARCHAR(50) NOT NULL COMMENT '操作',
  `target_type`   VARCHAR(30) DEFAULT NULL COMMENT '对象类型',
  `target_id`     BIGINT      DEFAULT NULL COMMENT '对象ID',
  `detail`        VARCHAR(500) DEFAULT NULL COMMENT '操作说明或审核意见',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_operation_log_operator` (`operator_id`, `created_at`),
  KEY `idx_operation_log_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台操作日志表';

-- ------------------------------------------------------------
-- 16. merchant_profile 商家店铺资料表（V9：商家工作台 / 商家资料）
--     商家 ID 即 user.id，与 user 表一对一，不重复创建用户表。
-- ------------------------------------------------------------
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
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_merchant_profile_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家店铺资料表（与用户一对一）';
