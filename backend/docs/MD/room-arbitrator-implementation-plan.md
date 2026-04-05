# 仲裁辩论回滚后修复实施蓝图

## 1. 当前问题定位

当前代码回滚后，仲裁辩论链路存在 6 个关键问题：

1. `startDebate` / `startNextRound` 直接调用 `clientChat`，首发与下一轮首发没有经过 `dispatch` 规则树。
2. 仲裁候选规则过宽，LLM 只排除上一位 speaker，没有严格收口到“对侧阵营候选 + clientId 精确命中”。
3. `clientChat` 空响应、超时、Bean 缺失时只打日志，不发内部续跑事件，导致一位辩手失败后整轮停住。
4. `stopDebate` 只改状态，没有调度护栏，路上的旧决策仍可能继续执行。
5. `DebateSessionEntity` 的派生 getter 参与了 Redis 序列化，旧缓存中的 `finished/running/allDebaterIds` 会导致反序列化失败。
6. 前端辩论面板显隐被状态强绑，无法真正收起；`ROUND_END` 没有弹窗裁决入口；系统通知视觉权重过高，影响观察主辩论流。

## 2. 本轮修复目标

本轮修复后的目标能力：

- 用户可设置仲裁者 `clientId`
- 用户可指定正反方 `clientId`
- 开始辩论后，首位辩手与后续辩手都必须通过 `dispatch` 规则树选出并执行
- 首轮第一位发言者由仲裁者自由选择
- 从第二手开始，只允许上一位发言者的对侧阵营进入候选集
- 单个 `client` 超时、空响应、装配失败时，本轮依然能继续推进
- `ROUND_END` 后前端弹窗裁决胜方
- 停止辩论后，后续旧调度必须被完全切断

## 3. 后端主链设计

### 3.1 统一调度入口

- 新增内部事件：
  - `DEBATE_DISPATCH_TRIGGER`
  - `CLIENT_MSG_ERROR`
- `RoomDispatchService.dispatchNextSpeaker(...)` 统一支持：
  - `USER_MSG`
  - `CLIENT_MSG_END`
  - `CLIENT_MSG_ERROR`
  - `DEBATE_DISPATCH_TRIGGER`
- 规则树职责：
  - `AtMentionNode`：只处理用户显式 `@`
  - `ArbitratorNode`：只识别辩论模式并调用 `debateService.decideNextDispatch(...)`
  - `ProbabilityNode`：只处理自由聊天
  - `ChatExecutionNode`：唯一允许执行 `clientChat(roomId, clientId)` 的节点

### 3.2 DebateService 收口

- `startDebate` / `startNextRound`
  - 不再直接 `clientChat`
  - 事务提交后发布 `DEBATE_DISPATCH_TRIGGER`
- `decideNextDispatch(...)`
  - 只处理 `CLIENT_MSG_END`、`CLIENT_MSG_ERROR`、`DEBATE_DISPATCH_TRIGGER`
  - `CLIENT_MSG_END`
    - 记录 `ai_debate_record`
    - 推进 turn
    - 到达 `turnsPerRound` 则进入 `ROUND_END`
    - 未结束则继续仲裁下一位
  - `CLIENT_MSG_ERROR`
    - 记一条系统通知
    - 本次机会计入 turn
    - 未结束则继续仲裁下一位
    - 已结束则进入 `ROUND_END`
  - `DEBATE_DISPATCH_TRIGGER`
    - 只负责首发仲裁或下一轮首发仲裁

### 3.3 仲裁候选规则

- 所有身份只认 `clientId`
- 首轮首发：
  - 候选集 = 全部合法辩手
- 第二手开始：
  - 候选集 = 上一位发言者对侧阵营辩手
- 同阵营内部优先级：
  1. 本轮未发言者优先
  2. 发言次数少者优先
  3. 入房更早者优先
  4. `clientId` 兜底稳定排序
- `ArbitrationPromptContextVO` 增加：
  - `candidateSpeakerIds`
  - `preferredSpeakerIds`
  - `candidateJoinOrder`
  - `lastSpeakerClientId`
  - `lastSpeakerSide`
  - `speakerHistoryStats`

### 3.4 仲裁提示词与校验

- LLM 只能返回：

```json
{"speakerId":"client_xxx","reasoning":"..."}
```

- Prompt 明确硬约束：
  - 只能返回候选 `clientId`
  - 不能返回 `clientName`
  - 不能返回仲裁者自己
  - 不能返回上一位 speaker
- Prompt 明确偏好：
  - 优先回应上一条核心论点
  - 优先 `preferredSpeakerIds` 中更靠前的人
  - 若不选优先第一位，必须说明原因
- 后端硬校验：
  - `speakerId` 非空
  - `speakerId` 命中候选集
  - `speakerId` 属于当前活跃 session
- 失败降级：
  - 自动使用 `preferredSpeakerIds` 第一位

### 3.5 执行鲁棒性

- `RoomChatService.clientChat(...)`
  - 明确区分：
    - `TIMEOUT`
    - `EMPTY_RESPONSE`
    - `EXECUTION_FAILED`
  - 失败时只发内部 `CLIENT_MSG_ERROR`，不让链路静默中断
- 超时配置：
  - `room.debate.arbitrator-timeout-seconds=45`
  - `room.debate.speaker-timeout-seconds=90`
- `ChatExecutionNode`
  - 执行前先校验：
    - session 是否仍活跃
    - 当前决策的 `sessionId / round / version` 是否仍匹配
  - 落 `pendingSpeakerId / pendingTurnNumber / pendingDecisionReasoning / pendingDecisionSource`
  - 再发 `HOST_INTRO`
  - 最后才执行 `clientChat`

### 3.6 停止辩论与 Redis 修复

- `DebateSessionEntity`
  - 类级忽略未知字段
  - `getAllDebaterIds()` / `isRunning()` / `isFinished()` 显式不参与序列化
- `ChatRoomRepository.queryActiveDebateSession(roomId)`
  - Redis 反序列化失败后删除坏缓存
  - 回源数据库并重建干净缓存
- `RoomDebateStateVO` 增加调度护栏字段：
  - `pendingSpeakerId`
  - `pendingTurnNumber`
  - `pendingDecisionSource`
  - `pendingDecisionReasoning`
  - `dispatchSessionId`
  - `dispatchRound`
  - `dispatchVersion`
- `stopDebate(roomId)` 固定顺序：
  1. session 置 `FINISHED`
  2. 清空 `activeDebateSessionId`
  3. 清空全部 `pending*`
  4. 清空轮次待裁决快照
  5. after-commit 发送 `DEBATE_STOP`

## 4. 前端设计修正

### 4.1 面板显隐

- `showDebatePanel` 作为唯一显隐开关
- 有活跃辩论时只自动展开一次
- 用户手动收起后，普通状态刷新不再强制打开
- `DEBATE_STOP` 后自动收起

### 4.2 ROUND_END 裁决弹窗

- `waitingForWinner=true` 时自动弹窗
- 操作：
  - `判定正方胜`
  - `判定反方胜`
  - `稍后处理`
- 弹窗去重键：
  - `sessionId + roundNumber`
- 页面刷新后，如当前仍处于 `ROUND_END`，弹窗应恢复

### 4.3 消息流布局

- 主消息流优先展示用户与辩手发言
- 系统通知与主持调度消息改为弱化样式：
  - 小字号
  - 轻边框
  - 不再用居中高亮大卡片
- `HOST_INTRO` 保留，但降视觉权重，不抢主辩论内容

## 5. 日志与注释规范

- 统一日志阶段标签：
  - `DEBATE_START`
  - `ARBITRATOR_DECIDE`
  - `HOST_DISPATCH`
  - `CHAT_EXECUTE`
  - `CLIENT_MSG_END`
  - `CLIENT_MSG_ERROR`
  - `ROUND_END`
  - `ROUND_WINNER`
  - `DEBATE_STOP`
- 关键日志必须打印：
  - `roomId`
  - `sessionId`
  - `currentRound`
  - `currentTurn`
  - `speakerId`
  - `decisionSource`
  - `candidateSpeakerIds`
  - `pendingSpeakerId`
- 注释只写在：
  - 领域状态机
  - 规则树入口
  - 仲裁决策
  - 执行护栏
- 注释解释“为什么”和“不变量”，不重复代码动作

## 6. 验证标准

### 6.1 后端

- `startDebate` / `startNextRound` 不再直接 `clientChat`
- 所有辩论发言都经过 `dispatch` 规则树
- `CLIENT_MSG_END` 后能继续仲裁下一位
- `CLIENT_MSG_ERROR` 不会卡死整轮
- Redis 中存在旧字段 `finished/running/allDebaterIds` 时仍能恢复 session
- `stopDebate` 后不得再产生新的主持调度和后续发言

### 6.2 前端

- 辩论面板可以手动关闭
- `ROUND_END` 自动弹出裁决弹窗
- 刷新页面后弹窗可恢复
- `DEBATE_STOP` 后面板与弹窗都关闭
- 系统通知不会再压住主辩论消息流

### 6.3 编译

- 后端：
  - `mvn -o -f backend\\pom.xml -pl ai-agent-domain,ai-agent-trigger -am -DskipTests compile`
  - `mvn -o -f backend\\pom.xml -pl ai-agent-infrastructure -am -DskipTests compile`
- 前端：
  - `npm run build`

## 7. 本轮默认约束

- 本轮不处理 WebSocket 身份鉴权
- 每轮胜方继续由用户手动裁决
- 模型调用只延长超时，不做自动重试
- `room` 领域继续保持单仓储，不拆 `DebateRepository`
