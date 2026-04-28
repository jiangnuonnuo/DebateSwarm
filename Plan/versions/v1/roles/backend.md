# Role Summary: backend (v1)

## Active Agents
- backend-001

## Current Responsibilities
- Week1（2026-04-27 ~ 2026-05-03）交付 B 模式主链路最小闭环：
- `create -> context/update -> material/upload -> submit -> publish -> retry`
- 对齐状态机与执行树事件路径，确保生命周期回写一致。
- 在上下文契约中支持 `selectedAssetIds` 精准选图（不改表）。
- 补齐主流程单测（状态机/规则链/任务命令/执行结果分流）。

## Status Summary
- State: in_progress
- Sprint: Week1 B-mode
- Evidence policy: 每日最少 4 次更新 backend-001 agent 卡片（开工/中段/回归后/收工）

## Open Risks
- 不改库条件下，重试预算与并发幂等实现需依赖服务层约束，需重点覆盖单测与联调。
- MCP 外部依赖波动可能导致发布状态判定噪声，需通过错误码和日志收敛。

## Agent Registration
- backend-001: XHS V1 B模式 Week1 交付

## Week1 Frozen Scope
- In scope:
- B 模式图文发布主链路
- 精准选图优先级与 payload 规则完善
- 重试约束与并发幂等
- 主流程单元测试与构建回归
- Out of scope:
- A 模式审核闭环
- 数据库结构重构
