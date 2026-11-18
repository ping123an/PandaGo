# PandaGo 项目文档

- 产品需求：`../PRD_1.md`（ShopSphere 版本，项目名按用户指令改为 **PandaGo**，业务流程不变）
- 技术设计：`../TECH_DESIGN_3.md`
- 开发规范：`../AGENTS_4.md`
- `API.md` — 接口文档：健康检查、商品浏览/商家管理/审核（阶段 4）、购物车/收藏/地址（阶段 5）、订单（阶段 6）、模拟支付与状态查询（阶段 7）、发货/确认收货/评价/管理端（阶段 8）、Redis 缓存与超时关闭对接口的透明增强（阶段 9）
- `DATABASE.md` — 数据库设计说明与文档缺口记录（阶段 2 起，含阶段 4/5/6/7/8 增量，阶段 9 无 DDL，阶段 10 补超时扫描覆盖索引）
- `AUTH.md` — 认证与授权教学笔记：登录过滤链、JWT、Filter vs Controller、BCrypt、角色 vs 资源归属（阶段 3）
- `STAGE4_TESTS.md` / `STAGE4_LEARNING.md` — 阶段 4 测试与教学：分页、动态 SQL、索引、条件组合、DTO/VO、归属校验
- `STAGE5_TESTS.md` / `STAGE5_LEARNING.md` — 阶段 5 测试与教学：唯一索引、事务、N+1、用户 ID 来源、预估价 vs 成交价
- `STAGE6_TESTS.md` / `STAGE6_LEARNING.md` — 阶段 6 测试与教学：`@Transactional` 边界、传播与自调用陷阱、后端计价、快照、条件 UPDATE 防超卖、失败回滚矩阵
- `STAGE7_TESTS.md` / `STAGE7_LEARNING.md` — 阶段 7 测试与教学：订单状态机、模拟支付、唯一索引、幂等、条件更新、事务一致性、前端防重 vs 后端幂等
- `STAGE8_TESTS.md` / `STAGE8_LEARNING.md` — 阶段 8 测试与教学：角色权限三道防线、资源归属、状态流转、关联表与唯一索引、审核留痕、管理端接口安全
- `STAGE9_TESTS.md` / `STAGE9_LEARNING.md` — 阶段 9 测试与教学：Redis vs MySQL 职责、Cache Aside、缓存穿透/击穿/雪崩、缓存失效与降级、Spring Scheduling、定时任务幂等
- `STAGE10_TESTS.md` — 阶段 10 质量检查、118 项后端测试、前端构建、端到端演示、并发与数据一致性验证记录
- `DEPLOYMENT.md` — Docker Compose 部署说明：环境变量、启停/日志/重建命令、持久化、健康检查、旧库迁移
- `LEARNING_NOTES.md` — 开发涉及的 Java/Spring 知识点梳理（含代码位置与练习）
- `PROJECT_REVIEW.md` — 项目难点、解决方案、技术取舍与个人复盘
- `INTERVIEW_REVIEW.md` — 12 个面试主题：问法 / 基于真实代码的回答 / 追问 / 代码位置 / 红线
- `FINAL_ACCEPTANCE.md` — 阶段 10 最终验收报告（10 项验收结论、未实现功能、环境限制）
- `e2e_demo.py` — 端到端业务演示脚本（真实 HTTP 走完整购物链路，实测 22/22）

> 开发规范见根目录 `AGENTS_4.md`。需求以 PRD 为准，技术实现以 TECH_DESIGN 为准。
