# 真实会议模式架构设计 V1（架构评审版）

## 1. 目标与边界
- 输入基线：`docs/product/REAL-MEETING-MODE-PRD-V2.md`（2026-04-21 已冻结 V1 范围）。
- 本文目标：给出可实现的 DDD 架构方案，不进入生产代码实现。
- 本文边界：覆盖领域模型、分层落位、接口契约、事件流、存储设计、分阶段实施与验收证据。

## 2. 架构师职责学习（本项目落地版）
- 业务到技术翻译：把 PRD 的“会议流程语义”转成领域状态机、命令和事件，不丢规则、不增歧义。
- 边界治理：确保能力落在正确层（trigger/api/domain/infrastructure），避免跨层耦合。
- 风险前置：提前识别并发一致性、权限审计、回退闭环可追溯等高风险点并给出控制策略。
- 实施可交付：输出可拆分、可验收、可回归的迭代切片给 backend/frontend/tester。

## 3. 当前项目 DDD 映射（已实现）

### 3.1 模块分层映射
- `ai-agent-trigger`：入站适配层（REST、WebSocket、MQ Listener）。
- `ai-agent-api`：接口契约层（Controller 对应 DTO 合同）。
- `ai-agent-domain`：领域层（room/session/workspace/ai/user/admin 业务能力）。
- `ai-agent-infrastructure`：基础设施层（MyBatis DAO/Mapper、Redis、WebSocket 发布器）。
- `ai-agent-app`：运行时配置与组装。
- `ai-agent-types`：通用常量、异常、结果包装。

### 3.2 房间域当前主链路
- 用户消息：`ChatRoomSocketHandler -> RoomDispatchService.onMessage -> RoomChatService.onUserMessage`。
- 事件桥接：`RoomEventPublisher.publish` 同时对外广播 + 对内事件。
- 内部调度：`RoomAgentListener -> RoomDispatchService.dispatchNextSpeaker -> DispatchStrategyFactory`。
- 调度树：`DispatchRootNode -> AtMentionNode -> ArbitratorNode -> ProbabilityNode -> ChatExecutionNode`。
- 辩论会话：`DebateService` 负责会话状态机、轮次推进、仲裁结果回流、trace 护栏与乐观锁写入。

### 3.3 结论
- 当前属于“DDD 分层 + 规则树调度 + 事件驱动房间编排”。
- 真实会议模式可以复用现有房间事件总线、消息持久化、状态乐观锁机制。

## 4. 现状与 PRD V1 的差距
- 现状核心是“辩论调度（谁发言）”，PRD V1 需要“会议治理（谁汇报、谁退回、谁批准、谁指派）”。
- 现有 `DebateStatus` 不足以表达 `待开始/进行中/退回修改/待决策/已结论/下一轮待开` 会议语义。
- `@` 当前偏“响应触发”，PRD V1 还需要“结构化动作（汇报/退回/通过/结束/指派）+ 审计”。
- 权限侧当前仍有 TODO（如房间删除权限），会议治理必须先补齐角色动作鉴权。

## 5. 目标架构设计（V1）

### 5.1 领域边界（Bounded Context）
- `room-chat`（已存在）：消息收发、上下文装配、通用调度入口。
- `meeting-governance`（新增）：会议流程治理、动作命令处理、阶段推进与回退闭环。
- `debate-runtime`（已存在）：辩论子模式，作为会议模式下的可选协作策略能力。

### 5.2 聚合与值对象（meeting-governance）
- `MeetingSessionEntity`：会议主聚合（meetingId、roomId、topic、status、round、owner、pm、startedAt、endedAt）。
- `MeetingRoleAssignmentEntity`：会议内角色绑定（boss/pm/product/architect/executor/tester）。
- `MeetingActionEntity`：结构化动作流水（actionType、fromRole、toRole、payload、deadline、result）。
- `MeetingIssueEntity`：退回问题单（reason、requiredFix、dueAt、status）。
- `MeetingSummaryVO`：阶段结论、待决项、下一轮触发条件。
- `MeetingStatus`：`PENDING/IN_PROGRESS/RETURNED/WAIT_DECISION/CONCLUDED/NEXT_ROUND_PENDING/TERMINATED`。

### 5.3 状态机（核心）
1. `PENDING -> IN_PROGRESS`：会议创建并开始。
2. `IN_PROGRESS -> RETURNED`：项目经理或架构师发起退回并生成问题单。
3. `RETURNED -> IN_PROGRESS`：被退回方提交修订并重新进入评审。
4. `IN_PROGRESS -> WAIT_DECISION`：出现冲突待老板/项目经理裁决。
5. `WAIT_DECISION -> IN_PROGRESS`：裁决后继续流转。
6. `IN_PROGRESS -> CONCLUDED`：形成结论与行动项。
7. `CONCLUDED -> NEXT_ROUND_PENDING`：记录下一轮条件，等待老板触发。
8. 任意阶段 -> `TERMINATED`：手动终止（强制留痕）。

### 5.4 应用服务设计（domain service）
- `IMeetingService#createMeeting/startMeeting`
- `IMeetingService#submitReport`
- `IMeetingService#requestRevision`
- `IMeetingService#approveStage`
- `IMeetingService#raiseDecision`
- `IMeetingService#assignAction`
- `IMeetingService#concludeMeeting`
- `IMeetingService#terminateMeeting`
- `IMeetingService#queryMeetingStatus/queryMeetingTimeline`

### 5.5 分层落位（严格按现有工程规则）
- API 契约：`backend/ai-agent-api/.../dto/request|response/meeting/*`
- Trigger 入口：`backend/ai-agent-trigger/.../controller/MeetingController.java`
- Domain 业务：`backend/ai-agent-domain/.../room/service/meeting/*`
- Domain 仓储接口：`backend/ai-agent-domain/.../room/apapter/repository/IMeetingRepository.java`
- Infra 实现：`backend/ai-agent-infrastructure/.../repository/MeetingRepository.java`
- DAO/Mapper：`.../persistent/dao/IAiMeeting*Dao.java` + `resources/mapper/ai-meeting-*.xml`

### 5.6 事件与调度
- 保持现有 `RoomEventPublisher` 双通道机制（external/internal）。
- 新增会议事件类型（建议）：
  - `MEETING_ACTION_APPLIED`
  - `MEETING_STAGE_CHANGED`
  - `MEETING_ISSUE_CREATED`
  - `MEETING_CONCLUDED`
- 在调度树中新增 `MeetingGovernorNode`（位于 `AtMentionNode` 之后、`ArbitratorNode` 之前）：
  - 会议进行中时优先处理结构化动作。
  - 非会议指令回落到现有 `Arbitrator/Probability` 逻辑。

### 5.7 数据模型（建议）
- `ai_meeting_session`：会议主表（含状态、当前轮、主持人、裁决人、时间戳、version）。
- `ai_meeting_action`：动作流水（谁在何时对谁做了什么、payload、执行结果）。
- `ai_meeting_issue`：退回问题单（问题、修改要求、责任人、截止、状态）。
- `ai_meeting_summary`：结论与下一轮计划快照。
- `ai_chat_room_state.public_data`：保留运行时轻量状态指针（meetingId/currentStage），不塞重历史。

### 5.8 鉴权与审计
- 动作鉴权矩阵（最小版）：
  - `boss`：可任意 `@`、可终止、可裁决冲突、可触发下一轮。
  - `pm`：主持调度、退回、汇总结论、默认结束判断。
  - `architect`：可实现性评审与退回（附理由）。
  - `product`：提交/修订方案。
- 每个动作写 `ai_meeting_action`，并广播系统通知（便于 UI 与追溯）。

## 6. 前后端接口草案（V1）
- `POST /chat-room/meeting/create`
- `POST /chat-room/meeting/start`
- `POST /chat-room/meeting/action/report`
- `POST /chat-room/meeting/action/return`
- `POST /chat-room/meeting/action/approve`
- `POST /chat-room/meeting/action/decision`
- `POST /chat-room/meeting/action/conclude`
- `POST /chat-room/meeting/action/terminate`
- `GET /chat-room/meeting/status`
- `GET /chat-room/meeting/timeline`

前端（`RoomChat.vue`）最小改造：
- 新增“会议控制台”视图区分辩论状态与会议状态。
- `@` 操作支持动作模板（对象 + 动作 + 截止）。
- 系统通知卡片增加 `meetingActionType` 可视化标签。

## 7. 迭代落地建议（与 PRD 对齐）
- V1：会议骨架（状态机 + 主持调度 + `@` 汇报 + 结论与下一轮记录）。
- V2：退回闭环（问题单、修订任务卡、回原节点继续）。
- V3：执行调度（行动项池、执行状态追踪、老板可视化）。
- V4：治理增强（模板化流程、审计报表、复盘建议）。

## 8. 验收证据清单（架构视角）
- 代码证据：Meeting 聚合、服务、仓储、Mapper、Controller 全链路存在且依赖方向正确。
- 流程证据：至少一条“退回 -> 修订 -> 再评审 -> 通过”回放日志。
- 一致性证据：状态推进具备 version 护栏，过期回调被拒绝（与现有 trace 护栏一致）。
- 安全证据：非授权角色触发动作被拒绝并留痕。
- 可观测证据：timeline 可按 meetingId 回溯关键动作与责任人。

## 9. 当前轮结论
- 你当前项目已经具备实现真实会议模式的结构基础，不需要推翻重做。
- 推荐策略：以 `meeting-governance` 作为新子域增量接入，复用房间事件总线与状态护栏能力。
- 实施优先级：先做“状态机 + 动作审计 + 鉴权矩阵”，再做“自动化执行调度”。
