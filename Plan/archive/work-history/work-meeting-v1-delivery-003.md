# Work: work-meeting-v1-delivery-003

## Priority Status
- paused（2026-04-22）：被老板新需求 `work-merchant-value-bet-review-005` 覆盖，停止继续推进。

## Goal
- 基于已冻结 PRD，完成“真实会议模式 V1”可执行实施方案，并将后端、前端、测试任务拆分到可直接执行。

## Product Context
- User problem: 现有会议方案缺少可直接分派给研发团队的实施框架与任务拆解。
- Business value: 让老板可快速发起后端/前端协作，降低方案到实现的转换损耗。
- Non-goals: 本工作单不直接产出业务代码实现。

## Scope
- Included:
  - 读取产品知识清单并提炼 V1 小功能包。
  - 输出后端落地框架（模块、接口、数据、事件、鉴权）。
  - 输出前端落地框架（页面、组件、状态、接口联动）。
  - 输出可执行任务拆分（backend/frontend/tester）。
  - 生成团队分派计划文件并登记 Plan 状态。
- Excluded:
  - 后端/前端生产代码开发。
  - 自动化测试脚本实现。

## Acceptance Criteria
- 存在 1 份 V1 执行框架文档，覆盖产品知识来源、后端框架、前端框架、任务拆分。
- 存在 1 份团队任务计划，能直接指派给 backend/frontend/tester。
- 注册 backend/frontend/tester 角色代理并绑定本工作单。

## Role Assignments
| Agent | Role | Responsibility | Status |
|---|---|---|---|
| architect-1 | architect | 输出执行架构与任务拆分，维护跨层边界 | in-progress |
| product-1 | product | 校对产品范围与验收口径，防止方案偏离 | in-progress |
| backend-1 | backend | 负责后端实现切片接收与落地执行 | pending |
| frontend-1 | frontend | 负责前端实现切片接收与落地执行 | pending |
| tester-1 | tester | 负责状态机、权限、联调用例设计与验收 | pending |

## Dependencies
- 依赖 `docs/product/REAL-MEETING-MODE-PRD-V2.md` 与 `docs/product/INTERNAL-DISCUSSION-CONFERENCE-FUNCTIONS-V1.md`。
- 依赖已有架构基线 `docs/architecture/REAL-MEETING-MODE-ARCHITECTURE-V1.md`。

## Risks
- 若任务拆分不绑定 DoD，执行后容易出现“完成定义不一致”。
- 若前后端状态字段不统一，联调阶段会出现状态漂移。

## Manager Notes
- Created: 2026-04-21
- Assumption: 先交付可执行的实施框架，再由 backend/frontend 进入编码阶段。
- Update: 2026-04-22，因老板优先级变更，本工作单暂停，待后续重新排期。
