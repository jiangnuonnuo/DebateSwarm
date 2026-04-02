# 阶段 3：仲裁辩论赛闭环实施方案

## 1. 目标与范围

### 1.1 阶段目标

阶段 3 的目标不是接入 LLM 仲裁，而是先将“仲裁辩论赛”完整跑通，形成可闭环、可验证、可恢复的业务链路。系统需要支持：

- 设置房间仲裁者
- 开始辩论
- 自动推进辩手发言
- 达到 `turnsPerRound` 后结束当前轮次
- 广播轮次结束通知
- 等待用户宣布胜方
- 宣布胜方后进入下一轮
- 手动停止辩论
- 前端页面刷新后恢复当前辩论状态

### 1.2 本阶段范围

本阶段只实现“仲裁辩论赛”能力，不扩展到狼人杀、桌游或通用游戏状态机。

本阶段不引入 LLM 选择下一位发言者，而是使用确定性轮转规则，确保阶段 3 可以独立交付。

### 1.3 架构约束

- 保持 `room` 领域单仓储设计，不拆 `DebateRepository`
- 统一由 `IChatRoomRepository` / `ChatRoomRepository` 承接房间状态、辩论会话、辩论记录
- 规则树继续保留，但只负责识别模式与转发，领域规则统一收口到 `DebateService`

---

## 2. 当前基础与必须修正项

### 2.1 当前可复用基础

当前项目已经具备以下基础能力，可直接承接阶段 3：

- `RoomDispatchService -> DispatchStrategyFactory -> DispatchRootNode` 的事件驱动调度主链
- `AtMentionNode / ArbitratorNode / ProbabilityNode / ChatExecutionNode` 的规则树骨架
- `AiDebateSession` / `AiDebateRecord` 的持久化对象
- `IAiDebateSessionDao` / `IAiDebateRecordDao` 与对应 MyBatis Mapper
- `DebateSessionEntity` / `DebateRecordEntity` / `DebateStatus`
- `SystemNoticePayload` / `WebSocketEvent.SYSTEM_NOTICE` 事件类型
- 前端 `RoomChat.vue` 的消息流和 WebSocket 消费框架

### 2.2 必须修正项

在进入阶段 3 编码前，需要先修正以下问题：

#### 2.2.1 ArbitratorNode 逻辑不完整

当前 `ArbitratorNode` 只识别“是否有活跃辩论”，但未真正接管辩论链路，存在识别到辩论模式后仍继续落到 `ProbabilityNode` 的风险，容易出现：

- 辩论模式和自由聊天概率回复同时生效
- 当前轮已结束后仍继续选人
- 调度路径语义不一致

#### 2.2.2 DebateSessionEntity 过于贫血

当前 `DebateSessionEntity` 更接近字段载体，缺少状态机约束，导致以下核心业务规则没有被实体接管：

- 何时允许开始第一轮
- 何时允许结束当前轮
- 何时允许宣布胜方
- 何时允许开始下一轮
- 何时允许彻底结束辩论

#### 2.2.3 Mapper 查询字段不合理

当前部分 SQL 查询字段不完整或不准确：

- `ai-debate-session-mapper.xml` 中活跃会话查询字段不足，无法安全支撑调度主链
- `ai-chat-room-mapper.xml` 中 `queryExtConfigByRoomId` 使用了错误字段名 `roomid`

阶段 3 需要统一调整为“按场景最小字段读取”，既满足业务，又尽量命中索引。

#### 2.2.4 基线编译阻塞

当前 `ClientMemberService` 调用了 `IAiRepository.queryPromptByClientId(String clientId)`，但接口层未声明该方法，导致基线编译失败。该问题必须在阶段 3 前修复，否则后续改造无法完成编译验证。

#### 2.2.5 前端未处理 SYSTEM_NOTICE

前端当前只处理：

- `USER_MSG`
- `AGENT_MSG`
- `CLIENT_MSG`

尚未处理：

- `SYSTEM_NOTICE`

这意味着即使后端广播了 `ROUND_END`、`ROUND_WINNER` 等系统事件，前端也无法显示系统消息，也无法驱动辩论状态栏刷新。

---

## 3. 核心领域模型设计

### 3.1 DebateSessionEntity

`DebateSessionEntity` 是阶段 3 的核心业务实体，需要升级为充血模型，继续保留以下字段：

- `sessionId`
- `roomId`
- `topic`
- `arbitratorClientId`
- `proClientIds`
- `conClientIds`
- `turnsPerRound`
- `currentRound`
- `currentTurn`
- `roundWinners`
- `status`
- `version`

### 3.2 DebateSessionEntity 业务方法

阶段 3 需要在实体中明确实现以下行为：

- `startFirstRound()`
  - 将辩论启动为第一轮
  - 设置 `currentRound = 1`
  - 设置 `currentTurn = 0`
  - 设置 `status = RUNNING`

- `recordTurnFinished()`
  - 当前轮完成一次有效辩手发言后调用
  - 将 `currentTurn + 1`

- `isRoundComplete()`
  - 判断 `currentTurn >= turnsPerRound`

- `finishCurrentRound()`
  - 将 `status` 设置为 `ROUND_END`
  - 保留当前轮次与当前轮实际发言数
  - 不写入胜方

- `declareRoundWinner(String winnerSide)`
  - 仅允许在 `ROUND_END` 状态调用
  - 将当前轮的胜方写入 `roundWinners`

- `startNextRound()`
  - 仅允许在当前轮已宣判胜方后调用
  - 将 `currentRound + 1`
  - 将 `currentTurn = 0`
  - 将 `status = RUNNING`

- `finishDebate()`
  - 将 `status = FINISHED`

- `getSideForClient(String clientId)`
  - 返回 `PRO / CON / null`

- `validateDebater(String clientId)`
  - 校验成员是否属于正反方

- `nextSpeakerByRoundRobin(String lastSpeakerId)`
  - 阶段 3 确定性选人策略
  - 首位默认正方第一位
  - 后续正反交替
  - 阵营内轮转
  - 排除仲裁者

### 3.3 DebateRecordEntity

`DebateRecordEntity` 用于记录一次有效辩手发言，应包含以下字段：

- `recordId`
- `sessionId`
- `roomId`
- `roundNumber`
- `turnNumber`
- `speakerClientId`
- `speakerName`
- `side`
- `messageId`
- `arbitratorReasoning`
- `createTime`

该实体强调记录的是“有效辩手发言”，不是任意聊天消息，也不是系统通知。

### 3.4 RoomDebateStateVO

房间级辩论运行态继续使用 `ai_chat_room_state.public_data` 持久化，新增 `RoomDebateStateVO`，字段固定为：

- `arbitratorClientId`
- `activeDebateSessionId`
- `pendingRoundNumber`
- `pendingRoundTurnCount`
- `lastRoundEndedAt`
- `lastRoundSummary`

其中 `lastRoundSummary` 为轻量快照，建议包含：

- `roundNumber`
- `turnCount`
- `lastSpeakerId`
- `lastSpeakerName`
- `waitingForWinner`

该对象只承担“房间运行态快照”职责，不替代 `ai_debate_session` 的权威状态。

---

## 4. ROUND_END 与结果处理设计

### 4.1 ROUND_END 的业务语义

当 `currentTurn` 达到 `turnsPerRound` 时，系统必须进入“当前轮结束，等待用户宣布胜方”的状态。

`ROUND_END` 的含义是：

- 当前轮发言已经达到上限
- 当前轮不允许继续自动调度下一位辩手
- 系统正在等待用户裁决本轮胜方
- 此时不能直接写入 `roundWinners`

也就是说，`ROUND_END` 不是“本轮胜方已确定”，而是“本轮已结束，待裁决”。

### 4.2 达到 turnsPerRound 时的完整动作

当 `CLIENT_MSG_END` 触发后发现当前轮已达到 `turnsPerRound`，系统必须同时执行以下动作：

1. 保存本次 `DebateRecordEntity`
2. 将 `ai_debate_session.status` 更新为 `ROUND_END`
3. 将当前轮次快照写入 `ai_chat_room_state.public_data`
4. 写入一条 `SYSTEM_NOTICE` 类型的系统消息到 `ai_chat_room_message`
5. 停止继续调度下一位辩手
6. 让前端进入“待宣布胜方”状态

### 4.3 ROUND_END 不直接写赢家

系统在 `ROUND_END` 阶段不能直接写入 `roundWinners`。原因是：

- 当前设计中赢家由用户 API 触发决定
- 仲裁者在阶段 3 不负责判胜
- 若在轮次结束时直接写赢家，会破坏“用户裁决”业务语义

只有 `declareRoundWinner(String winnerSide)` 才能把胜方写入 `roundWinners`。

### 4.4 ROUND_END 系统消息设计

轮次结束时必须写入一条系统消息到 `ai_chat_room_message`：

- `senderType = SYSTEM`
- `messageRole = system`
- `senderId = system`
- `senderName = 系统通知`

消息 `content` 建议为可读文本，例如：

`第 1 轮辩论已结束，共完成 6 次发言，请选择本轮胜方。`

消息 `extData` 必须写入结构化 JSON，字段固定为：

- `noticeType`
- `sessionId`
- `roundNumber`
- `currentTurn`
- `turnsPerRound`
- `waitingForWinner`
- `topic`

示例：

```json
{
  "noticeType": "ROUND_END",
  "sessionId": "debate_xxx",
  "roundNumber": 1,
  "currentTurn": 6,
  "turnsPerRound": 6,
  "waitingForWinner": true,
  "topic": "AI 是否会替代大部分程序员"
}
```

### 4.5 ROUND_WINNER 结果处理

用户调用宣布胜方接口后，系统执行：

1. 校验 `session.status == ROUND_END`
2. 调用 `declareRoundWinner(winnerSide)`
3. 将当前轮胜方写入 `roundWinners`
4. 清理 `RoomDebateStateVO` 中的待裁决信息
5. 写入 `ROUND_WINNER` 系统消息

`ROUND_WINNER` 系统消息的 `extData` 建议包含：

- `noticeType`
- `sessionId`
- `roundNumber`
- `winnerSide`
- `roundWinners`

---

## 5. 单仓储接口扩展

阶段 3 继续保持单仓储设计，在 `IChatRoomRepository` 中按查询意图新增以下能力：

### 5.1 房间辩论运行态

- `RoomDebateStateVO queryRoomDebateState(String roomId)`
- `boolean saveRoomDebateState(String roomId, RoomDebateStateVO state, Integer version)`
- `void initRoomStateIfAbsent(String roomId)`

### 5.2 辩论会话

- `void saveDebateSession(DebateSessionEntity session)`
- `DebateSessionEntity queryDebateSessionBySessionId(String sessionId)`
- `DebateSessionEntity queryActiveDebateSession(String roomId)`
- `boolean updateDebateSessionProgress(DebateSessionEntity session)`
- `boolean updateDebateSessionStatus(DebateSessionEntity session)`

### 5.3 辩论记录

- `void saveDebateRecord(DebateRecordEntity record)`
- `List<DebateRecordEntity> queryDebateRecordsByRound(String sessionId, Integer roundNumber)`
- `List<DebateRecordEntity> queryLatestDebateRecords(String sessionId, Integer limit)`

### 5.4 Prompt / 状态辅助查询

- `DebateContextVO queryDebateContext(String sessionId)`

### 5.5 查询设计原则

仓储和 DAO 层必须遵循以下原则：

- 按场景最小字段查询
- 活跃调度查询优先命中索引
- 不写“大而全”的全字段查询方法
- 状态页成员名称通过房间成员仓储能力补齐，不做大 JOIN 扫描

---

## 6. 调度链与 DebateService 交互

### 6.1 DebateService 对外职责

阶段 3 新增：

- `IDebateService.java`
- `DebateService.java`

对外方法固定为：

- `setArbitrator(String roomId, String clientId)`
- `removeArbitrator(String roomId)`
- `startDebate(String roomId, String topic, List<String> proClientIds, List<String> conClientIds, Integer turnsPerRound)`
- `declareRoundWinner(String roomId, String winnerSide)`
- `startNextRound(String roomId)`
- `stopDebate(String roomId)`
- `queryDebateStatus(String roomId)`
- `handleDebateDispatch(String roomId, String eventType, AiChatRoomMessageEntity lastMessage)`

### 6.2 DebateService 核心职责

`DebateService` 需要统一收口以下领域逻辑：

- 校验仲裁者是否存在且属于房间中的 CLIENT
- 校验正反方成员都在房间中
- 校验正反方不重叠
- 校验仲裁者不能参与正反方
- 创建辩论会话并初始化第一轮
- 决定首位辩手
- 在 `CLIENT_MSG_END` 时记录本次辩手发言
- 推进 turn
- 判断是否达到 `ROUND_END`
- 判断是否可以开始下一轮
- 停止辩论并恢复普通聊天

### 6.3 ArbitratorNode 新逻辑

`ArbitratorNode` 在阶段 3 需要调整为以下行为：

1. 查询房间是否有活跃辩论
2. 若无活跃辩论，继续走 `ProbabilityNode`
3. 若有活跃辩论，调用 `debateService.handleDebateDispatch(...)`
4. 若返回 `DispatchDecisionVO`，直接流向 `ChatExecutionNode`
5. 若返回空，表示当前轮结束或无需调度，链路终止，不允许继续落到 `ProbabilityNode`

### 6.4 AtMentionNode 与辩论模式的关系

`AtMentionNode` 依旧保持最高优先级，但需要遵循以下规则：

- 辩论进行中，用户明确 `@` 某位辩手时，该辩手允许优先发言
- 当前轮处于 `ROUND_END` 时，不允许继续产生新的辩论发言
- 被 `@` 成功发言后，`DebateService` 仍按真实 speaker 记录本轮记录

---

## 7. API 与前端状态栏设计

### 7.1 阶段 3 API

阶段 3 需要在 `ChatRoomController` / `IChatRoomService` 中提供以下接口：

- `/chat-room/arbitrator/set`
- `/chat-room/arbitrator/remove`
- `/chat-room/debate/start`
- `/chat-room/debate/round/winner`
- `/chat-room/debate/round/next`
- `/chat-room/debate/stop`
- `/chat-room/debate/status`

### 7.2 DebateStatusDTO

`DebateStatusDTO` 需要增强为可直接驱动前端辩论状态栏，字段建议为：

- `sessionId`
- `topic`
- `status`
- `currentRound`
- `currentTurn`
- `turnsPerRound`
- `roundWinners`
- `arbitratorClientId`
- `arbitratorName`
- `waitingForWinner`
- `pendingRoundNumber`
- `lastRoundSummary`
- `proMembers`
- `conMembers`

其中 `lastRoundSummary` 建议包含：

- `roundNumber`
- `turnCount`
- `lastSpeakerName`
- `waitingForWinner`

### 7.3 前端现状缺口

当前 `RoomChat.vue` 存在以下缺口：

- 未处理 `SYSTEM_NOTICE`
- 未显示系统消息
- 没有辩论状态栏
- 无法承接 `ROUND_END` 后的宣布胜方操作

这些都是阶段 3 的必要改造项，不是可选优化项。

### 7.4 前端承接方案

前端采用“状态栏 + 按钮”方案，不使用强制弹窗。

状态栏应展示：

- 辩题
- 仲裁者
- 当前轮次
- 当前状态：`RUNNING / ROUND_END / FINISHED`

当 `waitingForWinner = true` 时，状态栏显示：

- `正方胜`
- `反方胜`
- `结束辩论`

当当前轮已宣判且会话未结束时，状态栏显示：

- `开始下一轮`

### 7.5 前端收到 ROUND_END 后的动作

前端收到 `SYSTEM_NOTICE(ROUND_END)` 后需要同时完成两件事：

1. 将系统通知插入消息流
2. 刷新或更新辩论状态栏

注意：

- 不允许只依赖即时 WebSocket
- 页面刷新后必须通过 `queryDebateStatus` 恢复当前待裁决状态
- 历史消息查询也要能够恢复系统通知时间线

---

## 8. 数据流时序

### 8.1 startDebate

`startDebate` 的完整链路：

1. 校验仲裁者存在且合法
2. 校验正反方成员都在房间中，且不重叠
3. 创建 `DebateSessionEntity`
4. 调用 `startFirstRound()`
5. 写入 `activeDebateSessionId`
6. 广播 `DEBATE_START`
7. 选择首位辩手
8. 调用 `clientChat`

### 8.2 CLIENT_MSG_END

每次客户端完成发言后：

1. 记录 `DebateRecord`
2. 调用 `recordTurnFinished()`
3. 判断 `isRoundComplete()`
4. 若未结束：
   - 继续选下一位辩手
   - 返回 `DispatchDecisionVO`
5. 若已结束：
   - 调用 `finishCurrentRound()`
   - 更新 `ai_debate_session.status = ROUND_END`
   - 更新 `RoomDebateStateVO`
   - 写 `ROUND_END` 系统消息
   - 终止调度链

### 8.3 declareRoundWinner

用户宣布胜方时：

1. 校验当前状态必须为 `ROUND_END`
2. 调用 `declareRoundWinner(winnerSide)`
3. 将结果写入 `roundWinners`
4. 清理待裁决运行态
5. 广播 `ROUND_WINNER`

### 8.4 startNextRound

进入下一轮时：

1. 校验当前轮已宣判
2. 调用 `startNextRound()`
3. 广播 `ROUND_NEXT`
4. 选择下一轮首位辩手
5. 调用 `clientChat`

### 8.5 stopDebate

停止辩论时：

1. 调用 `finishDebate()`
2. 将 `status = FINISHED`
3. 清空 `activeDebateSessionId`
4. 广播 `DEBATE_STOP`
5. 房间恢复普通聊天模式

---

## 9. 测试与验收标准

阶段 3 的验收需要分为三层。

### 9.1 领域层验收

- `finishCurrentRound()` 只置 `ROUND_END`，不写赢家
- `declareRoundWinner()` 只能在 `ROUND_END`
- `startNextRound()` 只能在当前轮已宣判后执行
- `finishDebate()` 后不允许继续推进轮次

### 9.2 服务层验收

- `handleDebateDispatch()` 达到 `turnsPerRound` 后：
  - 会更新 `ai_debate_session`
  - 会写 `RoomDebateStateVO`
  - 会写 `ROUND_END` 系统消息
  - 会停止继续调度

- `declareRoundWinner()` 后：
  - 会更新 `roundWinners`
  - 会清除待裁决状态
  - 会写 `ROUND_WINNER` 系统消息

- `startNextRound()` 后：
  - 会重置轮次计数
  - 会重新进入 `RUNNING`
  - 会能继续选下一位发言者

### 9.3 前后端联调验收

- 收到 `ROUND_END` 后，聊天流出现系统消息
- 收到 `ROUND_END` 后，状态栏出现“正方胜 / 反方胜 / 结束辩论”
- 页面刷新后仍能恢复待裁决状态
- 宣判胜方后，状态栏切换为“开始下一轮”
- 停止辩论后，系统恢复普通聊天模式

---

## 10. 实施顺序

建议按以下顺序落地阶段 3：

### 第一步：基线修复

- 修复 `IAiRepository.queryPromptByClientId` 接口声明
- 修复 `queryExtConfigByRoomId` 的字段名问题
- 统一 `AiDebateRecord` 的命名风格

### 第二步：实体充血化

- 完成 `DebateSessionEntity` 的状态机方法
- 补齐 `DebateRecordEntity.roomId`
- 新增 `RoomDebateStateVO`

### 第三步：仓储扩展

- 在单仓储内补齐房间运行态、辩论会话、辩论记录方法
- 调整 DAO / Mapper 为最小字段查询

### 第四步：DebateService 落地

- 设置仲裁者
- 开始辩论
- 处理 `CLIENT_MSG_END`
- 轮次结束
- 宣布胜方
- 下一轮开始
- 停止辩论

### 第五步：规则树整合

- 改造 `ArbitratorNode`
- 修正辩论模式下的链路终止逻辑

### 第六步：系统通知落库

- 扩展 `RoomChatService.publishSystemNotice`
- 落地 `ROUND_END / ROUND_WINNER / ROUND_NEXT / DEBATE_STOP`

### 第七步：API 与前端联动

- 增加辩论相关接口
- 增加 `DebateStatusDTO`
- 改造 `RoomChat.vue` 处理 `SYSTEM_NOTICE`
- 增加辩论状态栏与操作按钮

### 第八步：联调验证

- 验证自动推进
- 验证轮次结束
- 验证宣判胜方
- 验证下一轮继续
- 验证页面刷新恢复

---

## 结论

阶段 3 的核心不是“补几个接口”，而是把仲裁辩论赛从“可识别”推进到“可闭环运行”。

其中最关键的优化点是：

- `ROUND_END` 必须有明确业务语义
- `ROUND_END` 必须同时更新状态、记录快照、落系统消息、驱动前端待裁决 UI
- 前端不能只收系统消息，还必须能通过状态接口恢复当前轮次结果承接状态

只有这样，阶段 3 才算真正把“仲裁辩论赛”跑通，而不是只完成后端半截逻辑。
