# Version Work: v1

## Demand
- Demand ID: demand-001
- Version: v1

## Goal
- 形成「小红书智能发布」第一版可落地架构基线（V1 Baseline），作为后续接口与开发实现输入。

## Product Context
- User problem:
  - 用户需要在 Work 界面内完成小红书智能发布，支持人工可控与全自动两种发布体验。
- Business value:
  - 提升发布效率与一致性，降低人工拼接参数与重复操作成本，沉淀个人知识资产。
- Non-goals:
  - V1 不锁定智能择时策略；B 模式默认可直发，后续再扩展择时算法。
  - V1 不接入 OSS，图片先走测试态内存/临时存储。

## Scope
- Included:
  - Work 界面内引入独立发布功能入口（独立领域，界面集成）。
  - 双模式工作流：
    - A 模式：文案后审核、发布前审核。
    - B 模式：全自动执行，不设置人工审核节点。
  - 多 Agent 节点：
    - 选题/策划Agent
    - 文案Agent
    - 生图Agent（条件触发）
    - 参数组装Agent
    - 发布Agent
  - 素材策略：
    - 支持用户上传图片；
    - A 模式在文案审核节点决定是否补图；
    - B 模式自动补图。
  - 发布能力：
    - 发布前参数校验；
    - 发布成功必须返回帖子链接并在前端展示可点击 URL。
  - 知识库策略：
    - 个人库优先召回，再补共享库；
    - 共享库仅管理员可写；
    - 手动上传 + 发布成功自动沉淀个人库。
  - 任务上下文策略：
    - 窗口内存 + 每轮 JSON 快照；
    - 成功即删；
    - 失败保留 24 小时后自动清理。
  - 校验策略：
    - 校验智能体负责语义与平台适配校验；
    - 发布前规则网关负责硬约束兜底，避免无效 MCP 调用。
  - 定时发布结果策略：
    - V1 将“任务受理成功并持久化”作为成功；
    - 后续由用户手动确认最终帖子是否可见。
  - 账号策略：
    - V1 先跑通单用户默认账号；
    - 表结构预留多账号绑定与任务选账号能力。
  - 数据模型策略：
    - 任务、尝试、审核、模板、账号、素材、知识、快照拆表设计；
    - 图片二进制不入库，仅保留引用与元数据。
- Excluded:
  - B 模式智能择时细则（列为扩展功能）。
  - OSS 正式接入（列为后续迭代）。

## Acceptance Criteria
- 需求决策闭环：当前已确认规则全部可追踪到文档条目。
- 架构边界清晰：Trigger/API/Case/Domain/Infrastructure 职责明确。
- 发布工具最小权限：仅发布节点注入指定 MCP，其他节点不注入发布 MCP。
- 失败策略闭环：B 模式支持自动重生成重发布，并有三重上限约束。
- 可运维性：上下文快照与结果留痕策略明确。
- 数据可落地：存在可实施的核心表设计，能支撑任务、审核、重试、模板、知识沉淀与快照索引。

## Role Assignments
| Agent | Role | Responsibility | Status |
|---|---|---|---|
| architect-1 | architect | XHS 智能发布 V1 架构与流程基线设计 | in_progress |

## Dependencies
- Agent 运行时基础能力（已存在）：
  - Work 对话入口能力
  - Agent 调用链路
  - MCP Client 注入机制
- 指定发布 MCP：
  - Endpoint: `http://127.0.0.1:18060/mcp`
  - 仅发布节点注入

## Risks
- 测试期图片走内存存储，服务重启导致素材丢失（可接受于 V1 测试阶段）。
- B 模式允许全量参数变化，若约束不充分可能导致风格漂移，需要后续策略约束。
- 外部平台发布失败重试可能触发额外成本，需要严格执行三重上限。
- 若仅依赖 AI 校验而没有规则兜底，仍会出现无效 MCP 调用风险。

## Active Gaps
- 接口契约（字段级 Request/Response）尚未评审确认。
- 状态机事件表已形成，但尚未由后端按代码实现对齐。
- `createTask -> draft-only`、`context/update`、`submitTask` 三段式接口尚未正式改造。
- A/B 模式失败码与前端交互文案规范尚未冻结。
- 定时发布的“后续人工确认入口”尚未进入前端交互设计。

## Decision Artifacts
- `Plan/versions/v1/xhs-publish-architecture.md`
- `Plan/versions/v1/xhs-publish-delivery-plan.md`
- `Plan/versions/v1/xhs-publish-state-machine.md`

## Technical Mirrors
- `backend/docs/xhs-publish-v1.md`
- `backend/docs/xhs-publish-v1-delivery-plan.md`
- `backend/docs/xhs-publish-v1-state-machine.md`

## Manager Corrections
- This section is rewritten by manager verification when task gaps appear.

## Manager Notes
- Created: 2026-04-25 22:25
- Updated: 2026-04-25 22:32 by architect-1 (V1 baseline confirmed by PM)
- Updated: 2026-04-25 23:30 by architect-1 (DB/schema/validation/task-result strategy refined)
- Updated: 2026-04-26 00:20 by architect-1 (delivery plan + state machine promoted into Plan as source-of-truth)
