# Role Summary: backend

## Active Agents
- backend-1

## Current Responsibilities
- 真实会议模式 V1 后端落地：Meeting API、状态机、持久化、动作审计、问题单闭环。
- 严格遵循后端工程硬约束（高可用、高解耦、可观测、可审计）。

## Status Summary
- in-progress: 已完成会议治理核心链路开发，待 manager cycle 验证与跨角色联调。

## Open Risks
- 若数据库未执行 `meeting-table.sql`，会议持久化接口无法写入真实表。
- 若前端未携带 `expectedVersion` 与 `clientRequestId`，并发冲突和幂等能力会退化。

## Engineering Constraints
- 代码必须高可用、高解耦，需求频繁变更时可低成本扩展。
- 关键步骤需要注释，复杂流程需要职责注释，字段需要注释，不写尾注释。
- 日志必须清晰，关键链路统一输出：`roomId, meetingId, actionType, actorId, actorRole, fromStatus, toStatus, version, traceId`。
- SQL 必须命中索引，只查询必要字段，禁止 `select *`。
- 对象转换必须手写映射方法，禁止通用转换工具类。
- 方法不可过长，必须按职责拆分，保持控制层/领域层/仓储层解耦。

## Delivery Checklist
- 注释检查：关键流程、字段注释齐全，且无尾注释。
- 日志检查：成功、失败、重试日志分级明确，包含关键上下文字段。
- SQL 检查：核心查询提供索引命中依据，查询列最小化。
- 解耦检查：方法职责单一，转换手写，依赖方向符合 DDD 分层。

## Agent Registration
- backend-1: 真实会议模式V1后端落地：领域服务、接口、持久化、事件编排
