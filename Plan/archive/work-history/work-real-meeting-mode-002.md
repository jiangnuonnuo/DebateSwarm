# Work: work-real-meeting-mode-002

## Priority Status
- paused（2026-04-22）：被老板新需求 `work-merchant-value-bet-review-005` 覆盖，暂缓执行。

## Goal
- 将“房间辩论模式”升级为“真实会议化协作模式”的产品方案，并形成分版本落地计划。

## Product Context
- User problem: 当前模式偏讨论，不足以支撑真实小组会议的主持、汇报、退回、再评审和执行调度。
- Business value: 提升跨角色协作效率，保证需求从提出到执行分配全链路可控可追溯。
- Non-goals: 本工作单不进入代码实现，仅完成产品设计和规划。

## Scope
- Included:
  - 角色与权限模型定义（老板、项目经理、产品、架构、执行角色）。
  - 会议状态机与调度规则定义。
  - `@` 指派与汇报机制定义。
  - “退回修改 -> 再评审”闭环定义。
  - 架构可实现性评审与分层落位设计（DDD 映射、接口契约、存储与事件流）。
  - 分版本落地路线图（V1-V4）定义。
- Excluded:
  - API、数据库、前后端生产代码实现。
  - 自动化执行引擎的技术实现细节。

## Acceptance Criteria
- 输出 1 份甲方对接版 PRD 文档，覆盖 5 条核心诉求。
- 输出 1 份架构设计文档，明确 V1 的领域模型、状态机、接口与事件方案。
- 给出可执行的分版本迭代方案，明确每版目标和边界。
- 明确所有关键不确定项并提交甲方确认。

## Role Assignments
| Agent | Role | Responsibility | Status |
|---|---|---|---|
| product-1（产品经理 - 001） | product | 需求对齐、产品设计、版本规划、问题澄清 | in-progress |
| architect-1（架构师 - 001） | architect | DDD 架构评审、真实会议模式实现架构设计、技术分层与边界规划 | in-progress |

## Dependencies
- 甲方关键业务规则已确认，可进入 V1 基线冻结与架构评审。
- 需 backend/frontend/tester 角色按架构方案进入实现拆解与验收设计。

## Risks
- 若“老板可越级直接指令”缺少审计与通知机制，可能造成流程绕行和责任归属争议。
- 若手动终止缺少最小结论留痕，可能导致会后执行依据不足。

## Manager Notes
- Created: 2026-04-21 18:38
- Assumption: 先形成产品闭环方案，再进入技术评审与实施排期。
- Update: 2026-04-21 19:10，甲方已确认核心规则，V1 范围可冻结。
- Update: 2026-04-21 20:45，已创建执行工作单 `work-meeting-v1-delivery-003`，进入后端/前端可执行拆解阶段。
- Update: 2026-04-22，按老板最新优先级切换，当前工作单暂停。
