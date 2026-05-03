# Version Work: v1

## Demand
- Demand ID: demand-001
- Version: v1

## Goal
- 形成「小红书智能发布」第一版可落地架构基线（V1 Baseline），作为后续接口与开发实现输入。
- Week1（2026-04-27 ~ 2026-05-03）交付 B 模式主链路最小可运行闭环，并建立可持续更新的协作账本。
- Iteration 2（2026-05-04 ~ 2026-05-10）在不新增对外端点前提下完成重试治理、查询性能与安全加固。

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

## Iteration 2 Acceptance Criteria
- `IPublishRetryDomainService` 输出可解释决策结果（允许/拒绝 + 原因码），`retryTask` 不再内联 retry 分支判断。
- 任务分页仅查询轻量字段（不含 request/context/result/retryPolicy 大 JSON）。
- `IXhsPublishRepository` 支持 `latestAttempt + attemptStats` 聚合查询，避免 Java 侧全量遍历 attempts。
- 远程失败结果输出分类（transient/business/validation）且错误消息降噪。
- 主流程日志包含 `taskId/attemptId/event/errorCode/durationMs`，不打印正文大字段。

## Role Assignments
| Agent | Role | Responsibility | Status |
|---|---|---|---|
| architect-1 | architect | XHS 智能发布 V1 架构与流程基线设计 | in_progress |
| backend-001 | backend | XHS V1 B模式 Iteration2 交付（重试治理/性能/安全 + 主流程单测） | in_progress |

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
- Iteration 1 已封板（事实回写完成）：
  - `selectedAssetIds > images > task assets fallback` 已落地。
  - retry 三重约束（次数/总耗时/预算）与 JVM 内幂等防重已落地。
  - 路径安全与主流程单测已落地，回归通过。
- Iteration 2 当前活跃缺口：
  - 安全与性能证据已补齐：分页轻量映射、attempt 聚合驱动 retry、路径越界拒绝、远程失败分类降噪单测全部通过。
  - A 模式审核已支持驱动状态迁移（approved/resumed 与 rejected/fail 分流），不再仅写 review 记录。
  - A 模式最小审核闭环已可用：`A:image` 策略接入；submit 先入 `copy_review_pending`，通过后自动恢复执行。
  - 模板/知识沉淀已补齐关键能力：create 模板归属校验 + 空请求模板回填、publish_succeeded 自动沉淀个人知识（best-effort）。
  - 待完成：形成 Iteration 2 完成报告与 Iteration 3 输入（分布式幂等升级 + A 模式 pre_publish_review 阻断恢复闭环）。

## Iteration 1 Closure Evidence
- `mvn -s F:/java/code/Agent/.mvn-local-settings.xml -f backend/pom.xml -pl ai-agent-domain -am test -DskipTests=false` -> BUILD SUCCESS（Tests run: 20, Failures: 0, Errors: 0）
- `mvn -s F:/java/code/Agent/.mvn-local-settings.xml -f backend/pom.xml -pl ai-agent-domain,ai-agent-api,ai-agent-trigger,ai-agent-infrastructure -am -DskipTests compile` -> BUILD SUCCESS

## Iteration 2 Current Evidence
- `mvn -s F:/java/code/Agent/.mvn-local-settings.xml -f backend/pom.xml -pl ai-agent-domain -am test -DskipTests=false` -> BUILD SUCCESS（Tests run: 39, Failures: 0, Errors: 0）
- `mvn -s F:/java/code/Agent/.mvn-local-settings.xml -f backend/pom.xml -pl ai-agent-infrastructure -am test -DskipTests=false` -> BUILD SUCCESS（domain 39 + infra 5, Failures: 0, Errors: 0）
- `mvn -s F:/java/code/Agent/.mvn-local-settings.xml -f backend/pom.xml -pl ai-agent-domain,ai-agent-api,ai-agent-trigger,ai-agent-infrastructure -am -DskipTests compile` -> BUILD SUCCESS

## Iteration 2 Delivery Checklist (backend-001)
- Day1（2026-05-04）：Iteration 1 封板事实回写与账本对齐。
- Day2-Day3（2026-05-05 ~ 2026-05-06）：重试治理责任链化（DecisionContext/Result + 5 Guard）。
- Day3-Day4（2026-05-06 ~ 2026-05-07）：分页轻量查询与 attempt 聚合统计查询改造。
- Day4-Day5（2026-05-07 ~ 2026-05-08）：远程失败分类降噪与结构化日志加固。
- Day5-Day6（2026-05-08 ~ 2026-05-09）：主流程注释完善、可复用能力下沉、A 模式审核驱动状态补齐。
- Day6-Day7（2026-05-09 ~ 2026-05-10）：回归收敛、错误码联调与 Iteration 3 输入沉淀。

## Decision Artifacts
- `Plan/versions/v1/xhs-publish-architecture.md`
- `Plan/versions/v1/xhs-publish-delivery-plan.md`
- `Plan/versions/v1/xhs-publish-state-machine.md`
- 2026-05-03 架构补充决策：
  - B 发布主链路统一到 `xfg-wrench` 双树
  - controller DTO / response DTO 全量迁出 domain
  - XHS `dao/po/xml` 迁移到新目录，旧 `persistent/*` 中重复文件退场
  - B 发布节点树进一步收口为“节点自持逻辑、support 退场”，避免 `support.execute(context)` 重新演化为大 service
  - Agent fallback 改为显式参数契约，不再直接透传整包执行上下文

## Technical Mirrors
- `backend/docs/xhs-publish-v1.md`
- `backend/docs/xhs-publish-v1-delivery-plan.md`
- `backend/docs/xhs-publish-v1-state-machine.md`
- `backend/docs/xhs-publish-v1-error-codes.md`

## Manager Corrections
- This section is rewritten by manager verification when task gaps appear.

## Manager Notes
- Created: 2026-04-25 22:25
- Updated: 2026-04-25 22:32 by architect-1 (V1 baseline confirmed by PM)
- Updated: 2026-04-25 23:30 by architect-1 (DB/schema/validation/task-result strategy refined)
- Updated: 2026-04-26 00:20 by architect-1 (delivery plan + state machine promoted into Plan as source-of-truth)
- Updated: 2026-04-27 17:58 by backend-001 (Week1 B-mode implementation sprint started)
- Updated: 2026-04-28 16:05 by backend-001 (Iteration1 closed with evidence, Iteration2 active gaps switched in)
- Updated: 2026-04-28 16:42 by backend-001 (Iteration2 evidence refreshed, A-mode review-driven state writeback landed)
- Updated: 2026-04-28 16:51 by backend-001 (Template reuse + publish success knowledge ingestion landed, evidence refreshed to domain37/infra5)
- Updated: 2026-04-28 16:55 by backend-001 (A:image strategy + copy_review pending/resume landed, evidence refreshed to domain39/infra5)
- Updated: 2026-05-03 10:39 by architect-1 (B 发布架构重构完成，双树/DTO 边界/新 dao-po-xml 目录已落地并通过 compile + clean test-compile)
- Updated: 2026-05-03 16:37 by architect-1 (B 发布节点逻辑回收到节点本身，旧 support 退场，主工程重新 compile 通过)
