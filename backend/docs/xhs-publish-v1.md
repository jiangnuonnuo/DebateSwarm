# 小红书发布 V1 地基说明

## 设计目标
- 一次建全表，尽量避免后续频繁改库。
- 表职责独立，字段保持轻量，扩展信息优先放 JSON。
- MySQL 管业务真相，PGVector 管向量切片和召回。

## 8 张 MySQL 表职责
- `ai_xhs_publish_task`: 发布任务主表，承接前端任务列表、详情、最终结果。
- `ai_xhs_publish_attempt`: 每次执行尝试，支撑自动重跑、失败排查、预算统计。
- `ai_xhs_publish_review`: 统一审核和系统校验事件流。
- `ai_xhs_publish_content_asset`: 素材来源、存储、展示、过期清理。
- `ai_xhs_publish_knowledge_doc`: 知识主档，管理 personal/shared 元数据。
- `ai_xhs_publish_template`: 个人模板资产。
- `ai_xhs_publish_account_binding`: 默认账号与未来多账号绑定。
- `ai_xhs_publish_snapshot`: 每轮快照文件索引与清理。

## PGVector 职责
- `public.vector_store_xhs_openai`: 小红书专用向量表，不复用现有通用表。
- personal/shared 同表管理，靠 metadata 区分 scope。
- 固定检索顺序：personal 优先，不足再补 shared。

## 代码地基
- `api`: 对外接口定义。
- `trigger`: Controller + 清理定时任务骨架。
- `domain/xhspublish`: DTO/VO/Entity/Aggregate/Repository/四色领域对象/Port。
- `infrastructure/persistent`: PO/DAO/Mapper 骨架。
- `app`: XHS 发布专属配置与 PgVector Bean。

## 当前实现边界
- 本次只落 DDL、配置、类型、DAO/Mapper 和接口骨架。
- 实际发布流程、审核流程、MCP 调用、资产落盘、向量写入由后续开发补业务实现。

## 交付说明
- 面向前后端的详细落地说明见 `backend/docs/xhs-publish-v1-delivery-plan.md`。
