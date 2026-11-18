-- ============================================================
-- PandaGo 阶段 7 增量迁移：模拟支付记录
-- 可重复执行：CREATE TABLE IF NOT EXISTS，不 DROP、不清空数据。
-- 设计依据：PRD 2.1.11 / 5.4、AGENTS 6.7；TECH_DESIGN 第四章原文截断且库名为 shop，沿用 pandago。
-- ============================================================

USE `pandago`;

CREATE TABLE IF NOT EXISTS `payment_record` (
  `id`             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '支付记录ID',
  `payment_no`     VARCHAR(64)   NOT NULL COMMENT '支付流水号，后端生成且唯一',
  `order_id`       BIGINT        NOT NULL COMMENT '订单ID',
  `order_no`       VARCHAR(32)   NOT NULL COMMENT '订单号快照，便于按订单号查询',
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
