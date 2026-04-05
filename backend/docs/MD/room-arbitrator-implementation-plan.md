# 仲裁辩论实现与细化蓝图

## 1. 当前目标

当前阶段的稳定目标如下：

- 用户可设置仲裁者 `clientId`
- 用户可指定正方与反方 `clientId`
- 开始辩论后，首位发言者与后续发言者都必须通过 `dispatch` 规则树选出
- 仲裁结果只认 `clientId`，不允许按 `clientName` 参与任何执行判断
- 每轮结束后由用户手动裁决胜方
- 单个 `client` 失败、超时、空响应时，不允许卡死整轮
- 停止辩论后，旧执行和旧回调必须被彻底切断

## 2. 当前核心不变量

### 2.1 规则树是唯一执行入口

- `DebateService` 不允许直接执行 `clientChat`
- 以下场景都必须进入 `dispatch` 规则树：
  - `startDebate`
  - `startNextRound`
  - `CLIENT_MSG_END`
  - `CLIENT_MSG_ERROR`
- `ChatExecutionNode` 是唯一允许真正调用 `clientChat(...)` 的节点

### 2.2 只有成功发言才消耗 turn

- `turn` 表示本轮已经完成的有效发言次数
- `slot` 表示当前正在处理的逻辑发言位
- 只有 `CLIENT_MSG_END` 才能触发 `recordTurnFinished()`
- `CLIENT_MSG_ERROR` 不允许直接消耗 turn
- `CLIENT_MSG_ERROR` 必须先进入“同槽重选”

### 2.3 每次执行都必须携带唯一 `dispatchTraceId`

- 每次仲裁选出 speaker 后生成一次唯一 `dispatchTraceId`
- `dispatchTraceId` 必须贯穿：
  - `DispatchDecisionVO`
  - `RoomDebateStateVO`
  - `ClientExecutionRequestVO`
  - `CLIENT_MSG_END`
  - `CLIENT_MSG_ERROR`
- 回调处理时必须同时校验：
  - `activeDebateSessionId`
  - `pendingSpeakerId`
  - `dispatchTraceId`
  - `dispatchSessionId`
  - `dispatchRound`
  - `dispatchVersion`
- 任一不匹配都视为 stale callback，直接丢弃

## 3. 后端链路设计

### 3.1 调度入口

- 内部事件：
  - `DEBATE_DISPATCH_TRIGGER`
  - `CLIENT_MSG_END`
  - `CLIENT_MSG_ERROR`
- `RoomDispatchService.dispatchNextSpeaker(...)` 统一支持：
  - `USER_MSG`
  - `DEBATE_DISPATCH_TRIGGER`
  - `CLIENT_MSG_END`
  - `CLIENT_MSG_ERROR`

### 3.2 节点职责

- `AtMentionNode`
  - 只处理用户显式 `@`
  - 辩论模式下仅允许命中合法辩手
- `ArbitratorNode`
  - 只识别当前是否存在活跃辩论
  - 调用 `debateService.decideNextDispatch(...)`
- `ProbabilityNode`
  - 只处理自由聊天
- `ChatExecutionNode`
  - 写入 `pendingSpeakerId` 和调度护栏
  - 发布 `HOST_INTRO`
  - 构造 `ClientExecutionRequestVO`
  - 执行真正的 `clientChat(...)`

### 3.3 同槽重选模型

- 首轮首发：
  - 候选集 = 全部合法辩手
- 第二手开始：
  - 候选集 = 上一位成功发言者的对侧阵营
- 失败补位：
  - `CLIENT_MSG_ERROR` 到来后，不推进 turn
  - 固定要求仲裁者在同一方重选
  - 失败 speaker 加入 `slotAttemptedSpeakerIds`
  - `slotRetryCount + 1`
- 同槽候选优先级：
  1. 本轮未发言者优先
  2. 发言次数少者优先
  3. 入房更早者优先
  4. `clientId` 稳定排序
- 如果同侧候选耗尽：
  - 发布 `SLOT_SKIPPED`
  - 该槽显式跳过
  - 然后继续下一次攻防

### 3.4 Redis 与运行态

- `DebateSessionEntity`
  - 派生 getter 不参与 Redis 序列化
  - 类级忽略未知字段
- `ChatRoomRepository.queryActiveDebateSession(roomId)`
  - Redis 反序列化失败时必须删掉坏缓存
  - 然后回源数据库重建
- `RoomDebateStateVO` 当前关键字段：
  - `activeDebateSessionId`
  - `pendingSpeakerId`
  - `dispatchSessionId`
  - `dispatchRound`
  - `dispatchVersion`
  - `dispatchTraceId`
  - `slotRequiredSide`
  - `slotAttemptedSpeakerIds`
  - `slotRetryCount`

### 3.5 停止辩论

- `stopDebate(roomId)` 固定顺序：
  1. session 置为 `FINISHED`
  2. 清空 `activeDebateSessionId`
  3. 清空 `pendingSpeakerId`
  4. 清空 `dispatchTraceId`
  5. 清空 `slotRequiredSide / slotAttemptedSpeakerIds / slotRetryCount`
  6. 清空轮次待裁决快照
  7. after-commit 发送 `DEBATE_STOP`
- 任何旧回调只要护栏不匹配，都必须被丢弃

## 4. 仲裁提示词设计

### 4.1 硬约束

- 只能返回候选集内的 `clientId`
- 不能返回 `clientName`
- 不能返回仲裁者自己
- 不能返回当前槽已失败的 `clientId`
- 只能输出 JSON：

```json
{"speakerId":"client_xxx","reasoning":"..."}
```

### 4.2 输入上下文

- `topic`
- `currentRound / currentTurn / turnsPerRound`
- `lastSpeakerClientId / lastSpeakerSide`
- `requiredSide`
- `candidateSpeakerIds`
- `preferredSpeakerIds`
- `excludedSpeakerIds`
- `candidateJoinOrder`
- `speakerHistoryStats`
- `roundHistory`
- `recentConversation`
- `slotRetryCount`

### 4.3 失败降级

- LLM 返回为空、格式错误、候选外 `clientId`、超时时：
  - 自动降级到 `RoundRobinFallbackDecisionStrategy`
- fallback 也必须遵守当前候选集和同槽排除集

## 5. 前端设计

### 5.1 辩论面板

- `showDebatePanel` 是唯一显隐开关
- 有活跃辩论时首次自动展开一次
- 用户手动收起后，普通状态刷新不得强制再打开
- `DEBATE_STOP` 后自动收起

### 5.2 裁决弹窗

- `ROUND_END + waitingForWinner=true` 时自动弹窗
- 操作：
  - `正方胜`
  - `反方胜`
  - `稍后处理`
- 弹窗恢复键：
  - `sessionId + roundNumber`
- 页面刷新后，如果仍在待裁决状态，弹窗必须恢复

### 5.3 消息布局

- 主消息流优先展示真实发言
- `HOST_INTRO` 与系统通知弱化样式，不抢占主视图
- 新增系统通知语义：
  - `SPEAKER_ERROR`
  - `SLOT_SKIPPED`

## 6. 日志与注释规范

### 6.1 日志标签

- `DEBATE_START`
- `ARBITRATOR_DECIDE`
- `HOST_DISPATCH`
- `CHAT_EXECUTE`
- `CLIENT_MSG_END`
- `CLIENT_MSG_ERROR`
- `STALE_CALLBACK_DROPPED`
- `SLOT_RETRY`
- `SLOT_SKIPPED`
- `ROUND_END`
- `ROUND_WINNER`
- `DEBATE_STOP`

### 6.2 每条关键日志至少包含

- `roomId`
- `sessionId`
- `currentRound`
- `currentTurn`
- `speakerId`
- `dispatchTraceId`
- `pendingSpeakerId`
- `requiredSide`
- `slotRetryCount`

### 6.3 注释位置

- 领域状态机方法
- 规则树入口节点
- 执行护栏写入与校验
- stale callback 丢弃逻辑
- 同槽重选逻辑

注释只解释“不变量”和“为什么”，不重复代码表面动作。

## 7. 验证标准

### 7.1 场景回放

- 开始辩论后，首位 speaker 经过规则树选出并执行
- 任一辩手成功发言后，继续仲裁下一位
- 任一辩手失败后，不消耗 turn，而是同槽重选
- 同槽候选耗尽后，发布 `SLOT_SKIPPED` 并继续赛程
- 停止辩论后，任意旧回调都不能继续推进链路
- 停止后立即新开辩论，旧执行晚到时必须被丢弃

### 7.2 编译

- 后端：
  - `mvn -o -f backend\\pom.xml -pl ai-agent-domain,ai-agent-trigger,ai-agent-infrastructure -am -DskipTests compile`
- 前端：
  - `npm run build`

## 8. 本轮默认约束

- 本轮不处理 WebSocket 鉴权
- 本轮不处理房间成员安全校验
- 首轮第一位发言者继续由仲裁者自由选择
- 从第二手开始采用对侧阵营硬候选约束
- 每轮胜方继续由用户手动裁决
- `room` 大领域继续保持单仓储设计
