# XHS Publish Architecture Baseline

## Goal
- 在 Work 对话界面内落地「小红书智能发布」独立功能域。
- 复用现有 Work 会话、Agent 执行、RAG、MCP 注入能力，不复用不匹配的旧业务对象。
- V1 先跑通单用户默认账号、图文发布、A/B 两种流程、审核、失败重跑、知识沉淀。

## Integration Strategy
- 对话入口继续复用现有 `/ai/work/execute`，发布流程中的多 Agent 仍走现有执行链路。
- 新增独立子域 `xhs.publish` 承接结构化任务、审核、重试、结果、模板、账号、知识沉淀。
- 发布节点是唯一允许注入小红书 MCP 的节点，其他节点不注入发布 MCP。

## DDD Layering
- Trigger:
  - `XhsPublishController`：结构化任务接口、审核接口、模板接口、账号接口。
  - `XhsPublishJob`：24h 失败快照清理、本地素材清理、异步补偿任务。
- API:
  - `CreatePublishTaskDTO`
  - `SubmitReviewDTO`
  - `RetryPublishTaskDTO`
  - `SavePublishTemplateDTO`
  - `BindPublishAccountDTO`
  - `UploadPublishMaterialDTO`
- Case:
  - `XhsPublishTaskCase`
  - `XhsPublishReviewCase`
  - `XhsPublishRetryCase`
  - `XhsPublishTemplateCase`
  - `XhsKnowledgeIngestCase`
- Domain:
  - Aggregate: `PublishTaskAggregate`
  - Entity: `PublishAttemptEntity`, `PublishReviewEntity`, `PublishSnapshotEntity`, `PublishAccountEntity`, `PublishTemplateEntity`, `KnowledgeDocEntity`, `ContentAssetEntity`
  - VO: `PublishModeVO`, `PublishStatusVO`, `ReviewTypeVO`, `RetryPolicyVO`, `KnowledgeScopeVO`, `AssetStorageTypeVO`
  - Service: `PublishValidationService`, `PublishRetryPlanner`, `PublishContextService`, `PublishResultService`, `PublishKnowledgeService`
- Infrastructure:
  - MyBatis DAO/PO/XML
  - `XhsMcpGatewayAdapter`
  - `LocalTempMaterialAdapter`
  - `SnapshotFileAdapter`
  - `PgVectorKnowledgeAdapter`

## Workflow Model

### A Mode
- 选题/策划 Agent
- 文案 Agent
- 文案后审核
- 根据审核结果决定是否补图
- 生图 Agent（可跳过）
- 参数组装 Agent
- 发布前审核
- 发布 Agent

### B Mode
- 选题/策划 Agent
- 文案 Agent
- 自动补图判断
- 生图 Agent（条件触发）
- 参数组装 Agent
- 校验 Agent
- 发布 Agent
- 失败后按策略自动重生成并重发

## Validation Design
- 校验智能体负责语义校验：
  - 内容是否像小红书
  - 是否缺素材
  - 标题/正文/标签是否协调
  - 发布时间、可见性、账号目标是否合理
  - 是否建议退回补全
- 规则网关负责硬约束兜底：
  - 标题长度
  - 必填字段
  - 图片数量/文件可达性
  - `schedule_at` 格式与时间窗口
  - 可见性枚举
  - 发布模式与账号解析
- 结论：
  - 不采用“纯 AI 判定后直接调用 MCP”
  - 采用“校验智能体主判断 + 规则网关最终兜底”
  - 这样既保留灵活性，也能避免无效 MCP 调用

## Validation Agent Output Contract
- `decision`: `pass` / `fix` / `reject`
- `riskLevel`: `low` / `medium` / `high`
- `violations`: 问题列表
- `suggestions`: 修改建议列表
- `normalizedPayload`: 标准化后的发布参数
- `needImageSupplement`: 是否需要补图
- `needUserReview`: 是否需要人工确认

## Publish Success Definition
- 立即发布：
  - MCP 返回成功结果并拿到帖子实体时，状态记为 `published`
  - 若只拿到作业结果但未拿到帖子实体，状态记为 `accepted`
- 定时发布：
  - V1 以“任务受理成功并落库”作为成功
  - 用户后续手动确认最终帖子是否出现
  - 不把“已定时受理”误记为“已发帖可见”

## Account Strategy
- V1 默认单用户单默认账号先跑通。
- 任务表保留 `account_id` 字段，当前可为空。
- 当 `account_id` 为空时，由服务端解析当前用户默认绑定账号。
- 后续开启多账号时，前端只需要在任务创建时显式传 `account_id`。

## Material Strategy
- 图片二进制不入数据库。
- V1 素材先落本地临时目录，再转换为绝对路径供 MCP 调用。
- 资产表只存元数据、来源、引用路径、过期时间。
- 后续接入 OSS 时，仅替换 `ContentAsset` 存储适配器。

## Short-Term Memory
- 运行态记忆：
  - 窗口内存
  - 每轮 JSON 快照文件
- 快照内容：
  - 当前任务基础信息
  - 当前回合草稿参数
  - Agent 输出
  - 审核状态
  - 重试预算消耗
  - MCP 请求与响应摘要
- 清理策略：
  - 成功完成立即删除快照文件
  - 失败保留 24 小时后自动删除
  - 数据库保留摘要结果，不保留完整临时文件

## Long-Term Memory
- 个人知识库优先召回，共享知识库次之。
- 发布成功内容自动沉淀到个人知识库。
- 手动上传资料也进入个人知识库。
- 共享库仅管理员可写。
- 向量仍可复用现有 pgvector 能力，新增元数据表用于管理上传源、归属、状态与回溯。

## Retry Strategy
- 统一受三重上限约束：
  - 最大重跑次数
  - 最大总耗时
  - 最大成本预算
- 错误分类：
  - `RULE_REJECT`: 参数硬规则失败，不调 MCP
  - `VALIDATION_FIX`: 校验智能体要求修正
  - `MCP_QUEUE_FULL`: 队列满，退避后重试
  - `MCP_TRANSIENT_FAIL`: 网络或平台临时异常，允许原参数重试
  - `MCP_BUSINESS_FAIL`: 平台业务失败，允许内容和参数重生成
  - `MATERIAL_PREPARE_FAIL`: 素材准备失败，补图或重取素材
  - `BUDGET_EXCEEDED` / `TIMEOUT_EXCEEDED` / `RETRY_EXCEEDED`: 终态失败
- A 模式：
  - 优先返回用户补全或确认
- B 模式：
  - 自动走重生成与重发

## Proposed Tables

### V1 Must Have

#### 1. `ai_xhs_publish_task`
- 用途：任务主表，承接一次发布需求的全生命周期。
- 核心字段：
  - `id`
  - `task_id`
  - `session_id`
  - `user_id`
  - `agent_id`
  - `template_id`
  - `account_id`
  - `mode`
  - `status`
  - `current_stage`
  - `latest_attempt_no`
  - `need_image_policy`
  - `scheduled_publish_at`
  - `max_retry`
  - `max_total_duration_ms`
  - `max_cost_budget`
  - `used_retry_count`
  - `used_duration_ms`
  - `used_cost_amount`
  - `final_note_id`
  - `final_note_url`
  - `final_mcp_job_id`
  - `final_error_code`
  - `final_error_message`
  - `created_time`
  - `update_time`

#### 2. `ai_xhs_publish_attempt`
- 用途：每一次执行尝试的详细记录。
- 核心字段：
  - `id`
  - `attempt_id`
  - `task_id`
  - `attempt_no`
  - `trigger_type`
  - `status`
  - `planner_output_json`
  - `copy_output_json`
  - `image_output_json`
  - `param_output_json`
  - `validator_output_json`
  - `publish_request_json`
  - `mcp_tool_name`
  - `mcp_job_id`
  - `mcp_result_json`
  - `error_code`
  - `error_message`
  - `cost_amount`
  - `start_time`
  - `end_time`
  - `duration_ms`
  - `created_time`

#### 3. `ai_xhs_publish_review`
- 用途：审核节点记录，兼容 A 模式人工审核与 B 模式系统审核。
- 核心字段：
  - `id`
  - `review_id`
  - `task_id`
  - `attempt_id`
  - `review_type`
  - `review_mode`
  - `decision`
  - `reviewer_id`
  - `review_comment`
  - `review_payload_json`
  - `created_time`
  - `decided_time`

#### 4. `ai_xhs_publish_template`
- 用途：个人发布模板。
- 核心字段：
  - `id`
  - `template_id`
  - `user_id`
  - `template_name`
  - `template_desc`
  - `default_mode`
  - `topic_prompt_json`
  - `copy_prompt_json`
  - `image_prompt_json`
  - `publish_preset_json`
  - `retry_policy_json`
  - `is_default`
  - `created_time`
  - `update_time`

#### 5. `ai_xhs_account_binding`
- 用途：用户与小红书发布账号绑定关系。
- 核心字段：
  - `id`
  - `account_id`
  - `user_id`
  - `platform`
  - `account_name`
  - `mcp_endpoint`
  - `mcp_account_ref`
  - `status`
  - `is_default`
  - `ext_json`
  - `last_check_time`
  - `created_time`
  - `update_time`

#### 6. `ai_xhs_content_asset`
- 用途：素材元数据表，不存二进制，只存引用与生命周期。
- 核心字段：
  - `id`
  - `asset_id`
  - `user_id`
  - `task_id`
  - `attempt_id`
  - `asset_type`
  - `storage_type`
  - `storage_ref`
  - `preview_ref`
  - `checksum`
  - `source_type`
  - `status`
  - `expire_time`
  - `created_time`
  - `update_time`

#### 7. `ai_xhs_knowledge_doc`
- 用途：知识库元数据索引，配合 pgvector 使用。
- 核心字段：
  - `id`
  - `knowledge_id`
  - `user_id`
  - `knowledge_scope`
  - `knowledge_type`
  - `title`
  - `source_ref`
  - `rag_tag`
  - `summary`
  - `status`
  - `created_by`
  - `created_time`
  - `update_time`

#### 8. `ai_xhs_publish_snapshot`
- 用途：JSON 快照文件索引表。
- 核心字段：
  - `id`
  - `snapshot_id`
  - `task_id`
  - `attempt_id`
  - `round_no`
  - `stage`
  - `snapshot_path`
  - `snapshot_hash`
  - `cleanup_status`
  - `expire_time`
  - `created_time`
  - `deleted_time`

### Recommended Extension

#### 9. `ai_xhs_publish_event`
- 用途：任务时间线与问题排查。
- 核心字段：
  - `id`
  - `task_id`
  - `attempt_id`
  - `event_type`
  - `event_payload_json`
  - `event_time`

## Status Model
- `draft`
- `planning`
- `copy_review_pending`
- `image_generating`
- `param_building`
- `pre_publish_review_pending`
- `publishing`
- `accepted`
- `published`
- `failed`
- `cancelled`

## Interface Baseline
- `POST /xhs/publish/task/create`
- `POST /xhs/publish/task/detail`
- `POST /xhs/publish/task/page`
- `POST /xhs/publish/task/review`
- `POST /xhs/publish/task/retry`
- `POST /xhs/publish/template/save`
- `POST /xhs/publish/template/page`
- `POST /xhs/publish/account/bind`
- `POST /xhs/publish/account/default`
- `POST /xhs/publish/material/upload`
- `POST /xhs/publish/knowledge/upload`

## Current Recommendation
- 本次进入数据库设计评审时，建议先批准 8 张核心表。
- 如果想压缩 V1 实施量，可以把 `ai_xhs_publish_event` 放到 V1.1。
- `ai_xhs_account_binding` 即便当前只跑默认账号，也建议现在就建，避免后续补字段导致任务表返工。
