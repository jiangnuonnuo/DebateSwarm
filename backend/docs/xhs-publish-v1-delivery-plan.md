# XHS Publish Delivery Plan

## Position
- This file is the source-of-truth delivery decision document for V1 team collaboration.
- `backend/docs/*` are technical mirror documents for developers, but team-level planning and decision tracking should align here first.

## Goal
- 给前后端明确一版可直接开工的小红书发布 V1 落地方案。
- 明确周期拆分、职责边界、前后端分工、接口补点与关键校正项。

## V1 Scope
- Included:
  - 独立小红书发布页。
  - A/B 双模式。
  - 图文发布主链路。
  - 图片上传与 AI 补图能力预留。
  - 默认账号发布。
  - 任务/尝试/审核/素材/知识/模板/快照全量建模。
  - 个人知识库优先、共享知识库补充。
- Excluded:
  - OSS 正式接入。
  - 多账号管理界面。
  - 视频发布正式链路。
  - 智能择时算法。

## Delivery Principle
- Plan 是决策主账本。
- backend/docs 是技术实现镜像。
- 数据库一次建全，字段轻量。
- 运行流程不用大而全 service，必须采用：
  - 状态机
  - 规则链
  - 策略工厂
  - Port / Repository 分离
  - 守卫链 / Payload 构建器 / 远程执行器 / 生命周期记录器 分层

## Domain Adapter Boundary
- `adapter/repository`
  - 语义：领域定义的本地数据访问契约。
  - 典型对象：MySQL、Redis、配置表、任务主聚合读写。
  - XHS 当前口径：
    - `IXhsPublishRepository`：主聚合仓储，收口 `task/attempt/review/asset/snapshot`
    - `IXhsPublishTemplateRepository`
    - `IXhsPublishKnowledgeDocRepository`
    - `IXhsPublishAccountBindingRepository`
- `adapter/port`
  - 语义：领域定义的外部能力契约。
  - 典型对象：MCP、HTTP、RPC、文件系统、OSS、向量库。
  - XHS 当前口径：
    - `IXhsMcpPort`
    - `IXhsAssetStoragePort`
    - `IXhsSnapshotStoragePort`
    - `IXhsVectorStorePort`
- 统一规则：
  - Infrastructure 实现 `adapter/repository` 和 `adapter/port`
  - Domain Service / Node 只依赖接口，不直接依赖具体基础类
  - Infrastructure 不实现 Service 接口，不承担领域状态流转决定

## Execution Tree Baseline
- 参考现有 AI 领域 `rootNode -> router -> nextNode` 的执行树方式。
- XHS 发布主链路当前基线：
  - `BModeImagePublishStrategy`
    - 只保留入口和异常兜底
  - `XhsPublishRootNode`
  - `XhsPublishGuardNode`
  - `XhsPublishPayloadNode`
  - `XhsPublishRemoteNode`
  - `XhsPublishResultNode`
- 节点职责：
  - `RootNode`：初始化执行上下文与开始时间
  - `GuardNode`：执行前守卫链 + 状态机起跑
  - `PayloadNode`：规则链组装 payload + 发布前快照
  - `RemoteNode`：调用 MCP 远程执行器
  - `ResultNode`：结果分流、生命周期回写、快照清理/保留
- 两类链路同时保留：
  - 责任链：守卫链、payload 规则链
  - 树/节点链：发布执行树

## Critical Corrections

### 1. Create Task Must Be Draft-Only
- 决策：
  - `createTask` 只创建草稿任务，不允许立即执行。
- 原因：
  - 本地上传图片必须先拿到 `taskId`。
  - 如果创建即执行，图文发布在正式页面上会天然缺图。

### 2. Formal Submit Entry Is Required
- 必须新增：
  - `task/context/update`
  - `task/submit`
- 原因：
  - 前端需要先编辑上下文、上传素材，再触发执行。

### 3. Image Selection Must Not Default To All Assets
- 决策：
  - 前端应传 `selectedAssetIds`。
  - 后端按 `selectedAssetIds` 解析实际发布图片。
- 原因：
  - 避免一个任务多轮上传后把历史图片全部带进发布。

### 4. A Mode Review Must Drive State
- 决策：
  - A 模式审核不能只记录 review 表，必须推进/阻断任务阶段。

### 5. MCP Streamable Dependency Must Be Confirmed Before Joint Testing
- 决策：
  - `/mcp` 的 streamable HTTP client 依赖必须在联调前确认。

## Iteration Split

### Iteration 1
- Goal:
  - B 模式图文主链路跑通。
- Backend:
  - 草稿创建
  - 上下文更新
  - 素材上传
  - submit 执行
  - 默认账号初始化
  - MCP 发布
  - 结果回写
  - retry
- Frontend:
  - 创建页
  - 列表页
  - 详情页
  - 素材上传组件
  - 执行与结果展示

### Iteration 2
- Goal:
  - A 模式双审核跑通。
- Backend:
  - review 驱动状态机
  - 审核阻断与恢复
  - 系统校验与人工审核并存
- Frontend:
  - 审核卡片/抽屉
  - 审核通过/拒绝
  - 修改后再提交

### Iteration 3
- Goal:
  - 模板与知识沉淀跑通。
- Backend:
  - 模板保存/复用
  - 知识上传
  - 成功内容沉淀
  - pgvector 检索
- Frontend:
  - 模板选择
  - 知识上传页

## Backend Responsibility
- 统一维护任务状态机。
- 统一维护发布参数规则链。
- 统一通过策略工厂选择执行策略。
- 策略类只做入口选择，不再直接承担：
  - 参数组装
  - MCP 轮询
  - 快照保存
  - 状态回写
- 发布执行细节由执行树节点承担。
- 核心发布域本地数据访问统一收口到主聚合仓储：
  - `IXhsPublishRepository`
  - 覆盖 `task/attempt/review/asset/snapshot`
- Controller 不写业务逻辑。
- Repository 不写业务规则。
- Infrastructure 不直接决定状态流转。

## Frontend Responsibility
- 维护结构化表单，不直接编辑内部 JSON。
- 只维护 `assetId + accessUrl`，不持有 `storageRef`。
- 通过表单对象生成 `requestJson/latestContextJson`。
- 详情页必须展示：
  - task
  - attempt
  - review
  - asset
  - result

## Shared Context Contract
- Shared keys:
  - `title`
  - `content`
  - `images`
  - `selectedAssetIds`
  - `tags`
  - `products`
  - `visibility`
  - `schedule_at`
  - `is_original`
  - `batch_id`
  - `need_image`
  - `generate_image`
  - `knowledge_tags`
  - `ext`

## Implemented Framework Base
- 已落地三段式接口骨架：
  - `task/create`
  - `task/context/update`
  - `task/submit`
- 已将 `port/repository` 收口到领域 `adapter` 语义：
  - `domain/xhspublish/adapter/port`
  - `domain/xhspublish/adapter/repository`
- 已落地主聚合仓储：
  - `IXhsPublishRepository`
  - 统一封装 `task/attempt/review/asset/snapshot` 基础 CRUD
- 已删除主流程中的表式仓储接口，避免 domain 层继续按表驱动扩散
- 已落地执行树基础骨架：
  - `Root -> Guard -> Payload -> Remote -> Result`

## Decision Artifacts
- Delivery decision doc:
  - `Plan/versions/v1/xhs-publish-delivery-plan.md`
- State machine decision doc:
  - `Plan/versions/v1/xhs-publish-state-machine.md`
- Architecture baseline:
  - `Plan/versions/v1/xhs-publish-architecture.md`

## Technical Mirrors
- `backend/docs/xhs-publish-v1-delivery-plan.md`
- `backend/docs/xhs-publish-v1-state-machine.md`
- `backend/docs/xhs-publish-v1.md`
