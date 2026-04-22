# Work: work-v1-merchant-bet-review-001

## Goal
- 将当前会议产品需求切换为“商家价值对赌评审闭环”，并冻结 P0 功能范围。

## Product Context
- User problem: 现有会议方案偏通用协作，缺少“商家价值对赌”决策闭环。
- Business value: 让方案比较从主观讨论变成可裁决、可试点、可复盘的业务决策流程。
- Non-goals: 本工作单不进入代码实现，仅完成产品需求切换与功能冻结。

## Scope
- Included:
  - 定义对赌立项卡（A/B、指标、成本上限、风险红线、判胜条件）。
  - 定义证据强约束（数据依据/用户影响/实现成本/失败预案）。
  - 定义交叉质询回合（A 质询 B，B 反质询 A）。
  - 定义仲裁动作台（通过/退回补证/进入试点/终止）。
  - 定义决策单自动生成（结论、责任人、截止时间、验收指标）。
  - 定义试点跟踪与自动复盘机制。
- Excluded:
  - 后端、前端、数据库等技术实现。
  - 复杂 SLA/超时治理策略设计。

## Acceptance Criteria
- 输出 1 份 P0 冻结版 PRD，覆盖老板提出的 6 个必做功能。
- 旧任务状态明确标记为“暂停/被新需求替代”。
- 产品经理档案更新为新工作单并记录切换原因。

## Role Assignments
| Agent | Role | Responsibility | Status |
|---|---|---|---|
| product-1（产品经理 - 001） | product | 需求切换、P0 功能冻结、文档归档 | in-progress |

## Dependencies
- 需后续架构角色基于 P0 进行可实现性评审。

## Risks
- 若“判胜条件”定义不量化，试点阶段难以形成一致裁决。
- 若“风险红线”不前置固化，可能在试点阶段出现不可接受损失。

## Manager Notes
- Created: 2026-04-22
- Priority: Highest（老板新需求覆盖旧任务）
- Update: 2026-04-22，统一为单主线命名，作为 V1 唯一主工作单。
