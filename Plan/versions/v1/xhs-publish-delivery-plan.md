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
- `domain` 不允许承接 controller DTO / response VO。
- B 发布链路统一采用 `xfg-wrench`：
  - `AbstractMultiThreadStrategyRouter`
  - `StrategyHandler`
- 运行流程不用大而全 service，必须采用：
  - 状态机
  - 节点树
  - Facade Service
  - Port / Repository 分离
  - Support Service 能力下沉

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
- XHS B 发布当前统一为两棵树：
  - 智能预处理树
    - `BPublishIntelligentRootNode`
    - `BPublishClientCheckNode`
    - `BPublishInputNormalizeNode`
    - `BPublishDraftCreateNode`
    - `BPublishMaterialRegisterNode`
    - `BPublishCopyGenerateNode`
    - `BPublishContentNormalizeNode`
    - `BPublishContextPersistNode`
    - `BPublishSubmitDelegateNode`
  - 正式发布树
    - `BPublishExecuteRootNode`
    - `BPublishTaskLoadNode`
    - `BPublishBindingResolveNode`
    - `BPublishPreflightValidateNode`
    - `BPublishAttemptCreateNode`
    - `BPublishPayloadAssembleNode`
    - `BPublishMcpSubmitNode`
    - `BPublishMcpResultRouteNode`
    - `BPublishAcceptedPersistNode`
    - `BPublishSuccessPersistNode`
    - `BPublishFailurePersistNode`
    - `BPublishSnapshotFinalizeNode`
- `BModeImagePublishStrategy`
  - 只保留“根据 key 启动 B 正式发布树”职责。
- 已退场实现：
  - 自定义 `for` 循环守卫链
  - 一字段一规则类的 payload 规则拆法
  - 智能提交单独 guard 链

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
- 统一通过 `xfg-wrench` 节点树流转 B 发布。
- 策略类只做入口选择，不再直接承担：
  - 参数组装
  - MCP 调用
  - 快照保存
  - 状态回写
- 发布执行细节由执行树节点承担。
- 核心发布域本地数据访问统一收口到主聚合仓储：
  - `IXhsPublishRepository`
  - 覆盖 `task/attempt/review/asset/snapshot`
- Controller 不写业务逻辑。
- Repository 不写业务规则。
- Infrastructure 不直接决定状态流转。
- `XhsPublishService` 只保留门面职责，不再堆积全部业务实现。
- `api` 定义 request/response DTO：
  - `com.dasi.api.dto.request.xhspublish.*`
  - `com.dasi.api.dto.response.xhspublish.*`
- `trigger` 负责 DTO 与领域命令实体装配：
  - `XhsPublishApiAssembler`
- `domain` 只认识聚合、实体、值对象、命令实体。
- B 发布流转节点统一下沉到 `service/bmode/*`。
- 可复用能力当前保持为独立 service/support 类，不再继续堆进门面 service。
- `infrastructure` 中 XHS 相关持久化目录统一为：
  - `adapter/repository/xhspublish`
  - `adapter/port/xhspublish`
  - `dao/xhspublish`
  - `dao/po/xhspublish`
- XHS MyBatis XML 统一迁到：
  - `ai-agent-app/src/main/resources/mybatis/mapper/xhspublish`

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
- 已保留外部 URL 不变，同时将 controller DTO 全量移出 domain：
  - `api/dto/request/xhspublish/*`
  - `api/dto/response/xhspublish/*`
- 已将 domain 内部入参统一改为命令/查询实体：
  - `XhsPublishCreateTaskCommandEntity`
  - `XhsPublishUpdateContextCommandEntity`
  - `XhsPublishSubmitTaskCommandEntity`
  - `XhsPublishRetryCommandEntity`
  - `XhsPublishIntelligentSubmitCommandEntity`
- 已将 `port/repository` 收口到领域 `adapter` 语义：
  - `domain/xhspublish/adapter/port`
  - `domain/xhspublish/adapter/repository`
- 已落地主聚合仓储：
  - `IXhsPublishRepository`
  - 统一封装 `task/attempt/review/asset/snapshot` 基础 CRUD
- 已删除主流程中的表式仓储接口，避免 domain 层继续按表驱动扩散
- 已落地 B 模式两棵 `xfg-wrench` 节点树：
  - 智能预处理树
  - 正式发布树
- 已将 `XhsPublishService` 收口为门面实现，并归位到 `service/impl`
- 已完成 XHS `dao/po/xml` 目录迁移，删除旧 `persistent/*` 中的重复 XHS 文件
- 编译验证：
  - `mvn -f backend\\pom.xml -pl ai-agent-domain,ai-agent-api,ai-agent-trigger,ai-agent-infrastructure,ai-agent-app -am -DskipTests compile`
  - `mvn -f backend\\pom.xml -pl ai-agent-domain -am -DskipTests clean test-compile`

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
