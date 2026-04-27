# XHS Publish State Machine

## Position
- This file is the source-of-truth state machine decision document for V1.
- 后端实现 task / stage / attempt 流转时，以本文件为准。

## State Axes

### task_status
- `draft`
- `running`
- `accepted`
- `published`
- `failed`
- `cancelled`

### current_stage
- `planning`
- `copy_review_pending`
- `image_generating`
- `param_building`
- `pre_publish_review_pending`
- `publishing`
- `completed`
- `failed_terminal`
- `cancelled_terminal`

### attempt_status
- `created`
- `running`
- `waiting_review`
- `accepted`
- `published`
- `failed`
- `cancelled`

## Rule
- 只有状态机可以修改：
  - `task_status`
  - `current_stage`
  - `attempt_status`
- 业务入口只负责发事件，不直接写死迁移规则。

## Event Set
- `draft_created`
- `context_updated`
- `material_uploaded`
- `copy_review_required`
- `copy_review_approved`
- `copy_review_rejected`
- `image_generation_required`
- `image_generation_completed`
- `pre_publish_review_required`
- `pre_publish_review_approved`
- `pre_publish_review_rejected`
- `submit_requested`
- `payload_built`
- `validation_rejected`
- `publish_submitted`
- `publish_accepted`
- `publish_succeeded`
- `publish_failed`
- `retry_requested`
- `cancel_requested`

## Task Transition Table
| Event | From Status | From Stage | Guard | To Status | To Stage | Notes |
|---|---|---|---|---|---|---|
| `draft_created` | any | any | requestJson 合法 | `draft` | `planning` | 创建草稿 |
| `context_updated` | `draft/running/failed` | non-publishing | 当前用户有权限 | no-change | no-change | 更新上下文 |
| `copy_review_required` | `draft/running` | `planning` | A 模式 | `running` | `copy_review_pending` | 暂停等待文案审核 |
| `copy_review_approved` | `running` | `copy_review_pending` | review pass | `running` | `planning` | 继续执行 |
| `copy_review_rejected` | `running` | `copy_review_pending` | review reject | `running` | `copy_review_pending` | 停留等待修改 |
| `image_generation_required` | `running` | `planning` | 缺图且允许补图 | no-change | `image_generating` | 进入生图 |
| `image_generation_completed` | `running` | `image_generating` | 至少 1 图可用 | no-change | `param_building` | 生图完成 |
| `pre_publish_review_required` | `running` | `param_building` | A 模式 | no-change | `pre_publish_review_pending` | 等待发布前审核 |
| `pre_publish_review_approved` | `running` | `pre_publish_review_pending` | review pass | no-change | `publishing` | 允许提交 |
| `pre_publish_review_rejected` | `running` | `pre_publish_review_pending` | review reject | no-change | `pre_publish_review_pending` | 停留等待修改 |
| `submit_requested` | `draft/failed/running` | `planning/pre_publish_review_pending` | 账号、内容、图片、审核条件满足 | `running` | `planning` | 创建新 attempt |
| `payload_built` | `running` | `planning/image_generating` | payload 组装成功 | no-change | `param_building` | 写发布请求 |
| `validation_rejected` | `running` | `param_building` | payload 校验失败 | `failed` | `failed_terminal` | 写失败快照 |
| `publish_submitted` | `running` | `param_building/publishing` | MCP 已受理 | no-change | `publishing` | 写提交快照 |
| `publish_accepted` | `running` | `publishing` | 平台受理，未最终核验 | `accepted` | `publishing` | 常见于定时发布 |
| `publish_succeeded` | `running/accepted` | `publishing` | verify 成功 | `published` | `completed` | 删除成功快照 |
| `publish_failed` | `running/accepted` | `publishing/param_building` | 平台或本地执行失败 | `failed` | `failed_terminal` | 保留失败快照 |
| `retry_requested` | `failed` | `failed_terminal/publishing` | 未超重试次数/耗时/预算 | `running` | `planning` | 新建新一轮 attempt |
| `cancel_requested` | `draft/running/failed` | any | 用户有权限 | `cancelled` | `cancelled_terminal` | 终止任务 |

## Attempt Transition Table
| Event | From Attempt Status | To Attempt Status | Notes |
|---|---|---|---|
| `attempt_created` | any | `created` | submit/retry 新建 |
| `attempt_started` | `created` | `running` | 开始执行 |
| `attempt_waiting_review` | `running` | `waiting_review` | A 模式暂停 |
| `attempt_resumed` | `waiting_review` | `running` | 审核通过恢复 |
| `attempt_accepted` | `running` | `accepted` | 平台受理 |
| `attempt_published` | `running/accepted` | `published` | 平台成功 |
| `attempt_failed` | `created/running/accepted` | `failed` | 执行失败 |
| `attempt_cancelled` | `created/running/waiting_review` | `cancelled` | 任务取消 |

## Review Transition Table
| review_stage | Event | From Review Status | To Review Status | Notes |
|---|---|---|---|---|
| `copy_review` | `review_created` | any | `pending` | 文案审核 |
| `copy_review` | `review_approved` | `pending` | `approved` | 文案通过 |
| `copy_review` | `review_rejected` | `pending` | `rejected` | 文案拒绝 |
| `pre_publish_review` | `review_created` | any | `pending` | 发布前审核 |
| `pre_publish_review` | `review_approved` | `pending` | `approved` | 发布前通过 |
| `pre_publish_review` | `review_rejected` | `pending` | `rejected` | 发布前拒绝 |
| `system_validation` | `review_created` | any | `pending` | 系统校验 |
| `system_validation` | `review_approved` | `pending` | `approved` | 系统放行 |
| `system_validation` | `review_rejected` | `pending` | `rejected` | 系统拦截 |

## Guard Rules
- `submit_requested`
  - binding 可用
  - title 非空
  - content 非空
  - 图文模式必须有图或明确进入补图节点
  - A 模式必须完成前置审核
- `retry_requested`
  - 错误类型可重试
  - 未超过最大重跑次数
  - 未超过最大总耗时
  - 未超过最大成本预算
- `publish_succeeded`
  - verify 成功或平台明确成功

## Execution Structure Rule
- 状态机事件只能由以下入口触发：
  - `GuardNode`
  - `PayloadNode`
  - `RemoteNode`
  - `ResultNode`
- 业务层禁止在 Controller / Repository / Port Adapter 中直接写死：
  - `task_status`
  - `current_stage`
  - `attempt_status`
- `BModeImagePublishStrategy` 仅作为策略入口，不再承载完整执行细节。

## Persistence Rules
- `submit_requested`
  - update task
  - insert attempt
- `payload_built`
  - update task.latest_context_json
  - update attempt.publish_request_json
- `publish_submitted`
  - insert snapshot
- `publish_accepted`
  - update task.final_result_json
  - update attempt.publish_result_json
- `publish_succeeded`
  - update task / attempt terminal result
  - delete success snapshot
- `publish_failed`
  - update task / attempt terminal result
  - retain failure snapshot

## Current Code Corrections Needed
- 已完成：
  - `createTask` -> draft-only
  - add `submitTask`
  - add `updateTaskContext`
  - add `nextAttemptStatus`
  - extend `PublishStageVO` with:
    - `completed`
    - `failed_terminal`
    - `cancelled_terminal`
  - 执行链路调整为 `Root -> Guard -> Payload -> Remote -> Result`
- 下一步待业务层补全：
  - A 模式审核事件真正驱动状态机
  - `selectedAssetIds` 精准选图
  - payload 校验失败事件与 review/system_validation 联动
