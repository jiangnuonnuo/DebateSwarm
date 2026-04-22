# Team Next-Step Plan - work-meeting-v1-delivery-003

## Planning Window
- Start: 2026-04-22
- End: 2026-04-28
- Source:
  - docs/architecture/REAL-MEETING-MODE-V1-DELIVERY-FRAMEWORK.md
  - docs/product/REAL-MEETING-MODE-PRD-V2.md
  - docs/product/INTERNAL-DISCUSSION-CONFERENCE-FUNCTIONS-V1.md

## Priority Order
1. 落 Meeting 状态机与动作流水（后端先行）。
2. 打通前端会议控制台与动作提交（并行联调）。
3. 补齐权限矩阵与回归用例（测试兜底）。

## Feature Slice Breakdown (V1)
| Slice ID | Feature | Backend Owner | Frontend Owner | Tester Owner | Done Definition |
|---|---|---|---|---|---|
| V1-S1 | 会议创建与角色绑定 | backend-1 | frontend-1 | tester-1 | 可创建会议并展示角色绑定结果，接口与页面字段一致 |
| V1-S2 | 会议状态机与主持动作 | backend-1 | frontend-1 | tester-1 | 可执行开始/退回/通过/终止，状态迁移符合规则 |
| V1-S3 | 结构化 @ 动作 | backend-1 | frontend-1 | tester-1 | `@对象+动作+截止` 可提交并在时间线可见 |
| V1-S4 | 退回问题单与修订回流 | backend-1 | frontend-1 | tester-1 | 退回自动建问题单，修订后可回原节点 |
| V1-S5 | 会议结论与行动项输出 | backend-1 | frontend-1 | tester-1 | 项目经理可输出结论，行动项可查看与回放 |

## Agent Assignments
| Agent | Role | Next Task | Reason | Done Definition | Dependency |
|---|---|---|---|---|---|
| architect-1 | architect | 冻结 V1 字段字典与状态机迁移表，提交联调基线 | 避免前后端字段与语义漂移 | 发布字段字典与迁移图到 docs/architecture | product-1 |
| product-1 | product | 输出 V1 验收清单（按 S1-S5） | 保证交付口径统一 | 每个 Slice 有可核验验收项 | architect-1 |
| backend-1 | backend | 实现 Meeting API + Domain + Repository 最小闭环（S1-S3） | 后端为前端联调提供能力入口 | 提供可调用接口与基础数据回写 | architect-1 |
| frontend-1 | frontend | 实现 MeetingConsole + ActionComposer（S1-S3） | 建立可演示的会议主流程 | 页面可创建会议并发起结构化动作 | backend-1 |
| tester-1 | tester | 输出状态机/权限/时间线用例并跟进联调（S1-S3） | 早期发现流程与鉴权缺陷 | 提交测试清单并完成首轮验证 | backend-1, frontend-1 |

## Handoff Notes
- 后端接口字段命名优先复用现有 `chat-room` 风格，避免前端新增双模型。
- 前端仅以服务端状态为准，不做本地强行推进。
- 测试先做状态迁移与越权场景，再补体验层检查。

## Manager Follow-Up
- backend/frontend 完成 S1-S3 后，触发下一轮 manager cycle。
