# 真实会议模式 V1 落地框架（后端/前端执行版）

## 0. 输入知识清单（本方案依据）
- `docs/product/REAL-MEETING-MODE-PRD-V2.md`
- `docs/product/INTERNAL-DISCUSSION-CONFERENCE-FUNCTIONS-V1.md`
- `docs/architecture/REAL-MEETING-MODE-ARCHITECTURE-V1.md`

## 1. V1 小功能包（先做小而完整）

### F1 会议快速创建与角色绑定
- 功能目标：
  - 创建会议实例（议题、目标、约束、产出要求）。
  - 绑定角色成员（老板、项目经理、产品、架构、执行角色）。
- MVP 边界：
  - 支持从空白创建，不做模板中心。
  - 角色可替换，不做复杂权限后台。

### F2 会议状态机与主持动作
- 功能目标：
  - 支持状态迁移：`待开始 -> 进行中 -> 退回修改 -> 待决策 -> 已结论 -> 下一轮待开`。
  - 支持动作：通过、退回、补充提问、结束会议。
- MVP 边界：
  - 不做自动并发调度，仅做主持驱动迁移。

### F3 结构化 `@` 指令动作
- 功能目标：
  - 支持 `@对象 + 动作 + 截止` 的结构化提交。
  - 作为会议动作流水写入与广播。
- MVP 边界：
  - 动作类型先支持 `REPORT/RETURN/APPROVE/QUESTION/ASSIGN/TERMINATE`。

### F4 退回问题单与修订回流
- 功能目标：
  - 每次退回自动生成问题单（问题点、修改要求、截止时间、回报对象）。
  - 修订提交后可回到原节点继续评审。
- MVP 边界：
  - 不做复杂 SLA 预警。

### F5 会议结论与行动项输出
- 功能目标：
  - 由项目经理输出阶段结论与行动项。
  - 支持下一轮触发条件记录。
- MVP 边界：
  - 先存储并展示 JSON/Markdown，不做复杂导出中心。

## 2. 后端落地框架（基于现有 DDD 分层）

### 2.1 模块落位
- API 合同层：`backend/ai-agent-api/src/main/java/com/dasi/api/dto/{request,response}/meeting/*`
- Trigger 控制层：`backend/ai-agent-trigger/src/main/java/com/dasi/trigger/controller/MeetingController.java`
- Domain 领域层：`backend/ai-agent-domain/src/main/java/com/dasi/domain/room/service/meeting/*`
- Domain 仓储口：`backend/ai-agent-domain/src/main/java/com/dasi/domain/room/apapter/repository/IMeetingRepository.java`
- Infrastructure 仓储实现：
  - `backend/ai-agent-infrastructure/src/main/java/com/dasi/infrastructure/repository/MeetingRepository.java`
  - `backend/ai-agent-infrastructure/src/main/java/com/dasi/infrastructure/persistent/dao/IAiMeeting*Dao.java`
  - `backend/ai-agent-infrastructure/src/main/resources/mapper/ai-meeting-*.xml`

### 2.2 领域对象（V1）
- `MeetingSessionEntity`
- `MeetingActionEntity`
- `MeetingIssueEntity`
- `MeetingSummaryVO`
- `MeetingStatus`（状态枚举）

### 2.3 最小接口清单（V1）
- `POST /chat-room/meeting/create`
- `POST /chat-room/meeting/start`
- `POST /chat-room/meeting/action`
- `POST /chat-room/meeting/issue/resolve`
- `POST /chat-room/meeting/conclude`
- `POST /chat-room/meeting/terminate`
- `GET /chat-room/meeting/status`
- `GET /chat-room/meeting/timeline`

### 2.4 数据层最小表设计（V1）
- `ai_meeting_session`
- `ai_meeting_action`
- `ai_meeting_issue`
- `ai_meeting_summary`

说明：
- `ai_chat_room_state.public_data` 仅保留轻量索引（如 `activeMeetingId`），不存整段历史。
- 会议历史一律从 `ai_meeting_action` 追溯，确保可审计。

### 2.5 事件与一致性
- 复用现有 `IRoomEventPublisher`：
  - 外部：WebSocket 通知 UI。
  - 内部：应用事件驱动后续领域处理。
- 建议新增事件类型：
  - `MEETING_STAGE_CHANGED`
  - `MEETING_ACTION_APPLIED`
  - `MEETING_ISSUE_CREATED`
  - `MEETING_CONCLUDED`
- 并发控制：
  - 会议主状态写入使用 version 乐观锁。
  - 动作请求带 `meetingId + expectedVersion`，过期请求拒绝。

## 3. 前端落地框架（Vue3 + Pinia + RoomChat）

### 3.1 页面与组件拆分
- 保留入口：`frontend/src/components/RoomChat.vue`
- 新增子组件目录：`frontend/src/components/meeting/`
  - `MeetingConsole.vue`（会议状态与控制台）
  - `MeetingActionComposer.vue`（结构化动作提交）
  - `MeetingIssuePanel.vue`（退回问题单）
  - `MeetingTimeline.vue`（动作时间线）
  - `MeetingSummaryCard.vue`（结论与行动项）

### 3.2 状态与请求落位
- `frontend/src/router/pinia.js`
  - 新增 `meetingStore`（status、timeline、issues、summary、pendingAction）。
- `frontend/src/request/api.js`
  - 新增 `meeting*` API 方法，和后端接口一一对应。

### 3.3 前端交互规则（V1）
- 会议模式开关与辩论模式并存，会议动作优先展示。
- `@` 操作统一走结构化表单，不允许自由文本直接改状态。
- 所有状态改变以服务端返回和 WebSocket 事件为准。

## 4. 任务拆分框架（第一版）

### 4.1 后端任务包（backend-1）
- T-BE-01：建 Meeting 领域骨架与状态机。
- T-BE-02：落数据库表、DAO、Mapper、Repository。
- T-BE-03：落 MeetingController 与 DTO。
- T-BE-04：接入 RoomEventPublisher 事件广播。
- T-BE-05：落鉴权矩阵最小版（老板/项目经理/架构/产品）。

DoD：
- 接口可用，状态迁移符合规则。
- 动作流水可追溯。
- 非法角色动作可拦截。

### 4.2 前端任务包（frontend-1）
- T-FE-01：在 RoomChat 集成 MeetingConsole。
- T-FE-02：实现结构化动作提交面板。
- T-FE-03：实现会议状态、时间线、问题单、结论展示。
- T-FE-04：处理 WebSocket 会议事件增量更新。

DoD：
- 页面可完成创建、开始、退回、通过、终止、结论查看。
- 状态展示与后端一致，无本地伪状态漂移。

### 4.3 测试任务包（tester-1）
- T-QA-01：状态机迁移用例（正常流 + 异常流）。
- T-QA-02：权限矩阵用例（角色越权校验）。
- T-QA-03：动作流水与时间线一致性用例。
- T-QA-04：WebSocket 事件到 UI 同步用例。

DoD：
- 关键流程通过率 100%。
- 越权和过期版本请求均被拒绝。
- 时间线可回放完整动作。

## 5. 执行节奏建议（V1）
- 阶段 A（2 天）：后端骨架 + 前端页面骨架 + 联调桩。
- 阶段 B（3 天）：状态机与动作闭环 + 退回问题单。
- 阶段 C（2 天）：结论输出 + QA 回归 + 修复。

## 6. 老板验收口径
- 你可以重点看 5 个结果：
  - 能创建会议并绑定角色。
  - 能按主持动作推进状态。
  - 能退回并生成问题单再回流。
  - 能输出项目经理总结与行动项。
  - 能回放时间线并定位责任人。
