# Backend Review Triage - work-meeting-v1-delivery-003

## Owner Decision Context
- 决策时间：2026-04-22
- 决策原则：当前为“功能通路版本”，不是上线版本，按阶段分步落地。
- 执行口径：本轮优先修复影响 S1-S3 主流程闭环的问题；治理项进入后续阶段。

## Must Fix In Current Iteration (S1-S3 Gate)

### M1 - 状态机可恢复性
- 对应原问题：`CONCLUDE -> NEXT_ROUND_PENDING` 后无恢复路径。
- 结论：必须修。
- 原因：会直接阻断“下一轮待开 -> 下一轮开始”的主流程。

### M2 - 退回闭环目标角色
- 对应原问题：`RETURN` 未强制 `targetRole`，问题单可能失配/误关。
- 结论：必须修。
- 原因：会破坏“退回-修订-再评审”闭环，影响产品核心诉求。

### M3 - 轮次推进
- 对应原问题：`roundNumber` 不推进。
- 结论：必须修。
- 原因：S2 包含轮次展示与流转，不推进则验收口径不成立。

## Deferred To Governance Phase (Non-Blocking For This Iteration)

### D1 - 登录态身份强校验
- 对应原问题：请求体可伪造 `actorId/actorRole`。
- 本轮结论：延期，不作为当前阻塞。
- 后续阶段：V1.5（治理补强）前必须补齐。

### D2 - clientRequestId 非空约束
- 对应原问题：DDL 允许空值导致幂等语义不稳定。
- 本轮结论：延期，不作为当前阻塞。
- 后续阶段：V1.5（稳定性增强）前必须补齐。

## Delivery Rule
- backend-1 当前交付完成定义更新为：
  - 先修 M1/M2/M3 并提供代码与联调证据；
  - D1/D2 进入技术债清单，记录设计与改造方案，不阻塞当前联调。

