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

## 1.1 增量修复（2026-04-07）

- 轮间重配：`/chat-room/debate/round/next` 支持可选传入 `proClientIds`、`conClientIds`、`turnsPerRound`，仅在 `ROUND_END + 已宣判` 生效。
- 仲裁者固定：活跃会话内不支持切换仲裁者；下一轮只允许改正反方和发言次数。
- 状态自愈：新增会话恢复流程，收敛 `room_state` 与 `session` 脱锚状态，避免“已结束但无法开启新辩论”。
- 状态查询收口：`queryDebateStatus` 不再以“兜底查活跃 session”覆盖 room 运行态，防止孤儿会话误锁前端按钮。

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

## 9. 2026-04-07 落地修复记录

### 9.1 辩论主链稳定性

- `DebateService` 成为房间辩论运行态主写入口，常规仲裁路径不再由 `ChatExecutionNode` 写 `pendingSpeakerId`。
- `persistRoomDebateState` 增加有限重试（默认 3 次），冲突时回源数据库取最新 `version` 后重试。
- `startDebate` 首发链路调整为：`afterCommit -> DEBATE_DISPATCH_TRIGGER -> decideNextDispatch(写护栏) -> ChatExecutionNode 执行`。
- `ChatExecutionNode` 改为“护栏校验优先”，仅保留辩论中 `@` 指定的兼容补写路径。

### 9.2 stopDebate 强终止

- `stopDebate` 增加会话终止重试（默认 3 次），失败会打印 `DEBATE_STOP_FORCE` 相关日志。
- 新增 `forceSaveRoomDebateState`（不走 version 锁）用于人工显式停止时的强清理：
  - 清空 `activeDebateSessionId`
  - 清空 `pending*` 与调度护栏
  - 清空槽位重试状态与轮次待裁决快照

### 9.3 离线持续执行可观测

- 外部推送无在线会话时新增日志标签 `DEBATE_BACKGROUND_CONTINUE`，用于确认“页面离线但内部调度仍持续运行”。

### 9.4 Work 与 Repository 可用性

- Work 智能体列表查询增强为“创建来源 + SELF 仓库映射”合并去重，减少列表缺失。
- Work 下拉新增“已绑定（临时）”保活项，避免会话绑定 agent 因列表抖动被清空。
- Work 页面新增“发布到广场”按钮与发布弹窗（复用 `/workspace/agent/publish`）。
- Repository 的“我创建的”卡片新增发布按钮与发布弹窗（同接口复用）。

### 9.5 首发回流误判 stale 修复（2026-04-07）

- 根因：
  - `CLIENT_MSG_END` 成功回流消息未携带 `dispatchTraceId/dispatchSessionId/dispatchRound/dispatchVersion`。
  - `DebateService.matchesDispatchGuard` 强依赖上述字段，导致首发成功也会被判定为 stale。
- 修复动作：
  - `RoomChatService.clientChat` 成功路径补齐 `dispatch*` 元数据，并写入 `traceId=dispatchTraceId`。
  - `buildExecutionExtData` 统一新增 `callbackType`（`SUCCESS/ERROR`），成功与失败回流结构同构。
  - `RoomDispatchService.dispatchNextSpeaker` 的 `traceId` 装配改为三层优先级：
    1. `wsEvent.traceId`
    2. `payload.traceId`
    3. `payload.extData.dispatchTraceId`
  - `DebateService.matchesDispatchGuard` 改为“严格优先 + 兼容回退”：
    - extData 完整时走严格校验
    - extData 全缺失时允许 trace/state/pending 一致的兼容放行
    - extData 半残直接拒绝
- 新增日志标签：
  - `DEBATE_GUARD_STRICT_PASS`
  - `DEBATE_GUARD_FALLBACK_PASS`
  - `DEBATE_GUARD_REJECT`

### 9.6 ROUND_END 宣判后“开始下一轮”入口修复（2026-04-07）

- 根因：
  - 前端脚本已存在 `canStartNextRound` 计算，但面板模板未渲染“开始下一轮”按钮，导致用户宣判后只能看到“结束”。
- 修复动作：
  - `RoomChat.vue` 操作区新增“开始下一轮”按钮，绑定现有 `startDebateNextRound`。
  - `debate/status` 增加动作权限字段：
    - `canDeclareWinner`
    - `canStartNextRound`
    - `canStopDebate`
  - 前端优先使用后端权限字段渲染按钮；旧后端场景下保留本地兼容兜底判断。
- 交互目标：
  - `ROUND_END + waitingForWinner=true` 显示“裁决胜方”。
  - 宣判后（`ROUND_END + waitingForWinner=false`）显示“开始下一轮”。
  - `RUNNING/ROUND_END` 均允许“结束辩论”。

### 9.7 ROUND_END 轮间 @ 回复放行（2026-04-07）

- 背景：
  - 第一轮已宣判但第二轮未开始时，用户发送 `@client` 未触发响应，体验与预期不一致。
- 行为收敛：
  - `RUNNING`：保持“普通消息不自动回复”，仅按现有辩论规则推进。
  - `ROUND_END + waitingForWinner=true`：仍不放行 `@`（等待裁决阶段）。
  - `ROUND_END + waitingForWinner=false`：进入轮间窗口，仅放行 `@` 指定回复，未 `@` 普通消息继续拦截。
- 节点改造：
  - `AtMentionNode` 在轮间窗口把 `@` 按自由聊天路径处理（支持单 @ 与 Multi-At）。
  - `ArbitratorNode` 在轮间窗口拦截未 `@` 的 `USER_MSG`，不进入辩论推进逻辑。
- 新增日志：
  - `DEBATE_INTERMISSION_MENTION_ALLOWED`
  - `DEBATE_INTERMISSION_PLAIN_BLOCKED`

### 9.8 运行时动态提示词分层（2026-04-07）

- 目标：
  - 彻底阻断“入房改写 ai_prompt 导致提示词重复污染”的路径；
  - 保留静态装配链复用能力；
  - 将房间/辩论动态信息改为单次请求运行时注入。
- 分层规范：
  - 静态人设：继续通过 `ChatClient.defaultSystem(...)`（来自 DB 原始 prompt）；
  - 动态房间环境：在 `ContextAssemblerService` 每次请求生成 runtime header 注入；
  - 仲裁者动态指令：通过 `DebateRuntimeInstructionRenderer` 渲染 `debateInstruction`，仅本次仲裁调用生效。
- 落地动作：
  - `ClientMemberService` 下线 `rebuildSystemPrompt -> updateSystenByPromptId`，入房只做装配预热；
  - `RoomChatService` 在调用 `assemble` 时传入 `RoomRuntimePromptContextVO`；
  - `LlmArbitrationDecisionStrategy` 支持：
    - 若静态 system prompt 含 `{debateInstruction}`，运行时替换；
    - 若不含占位符，运行时追加动态仲裁指令；
    - 全流程不写回 DB。
- 新增日志：
  - `PROMPT_PERSIST_BLOCKED`
  - `PROMPT_RUNTIME_ASSEMBLE`
  - `ARBITRATOR_RUNTIME_INSTRUCTION_RENDERED`

### 9.9 装配收口与日志降噪（2026-04-07）

- 装配职责收口：
  - `service/context` 作为唯一提示词文本装配中心。
  - 普通聊天 runtime 头由 `RoomRuntimeHeaderAssembler` 组装。
  - 仲裁动态指令由 `DebateInstructionAssembler` 组装。
  - `ContextAssemblerService` 统一输出 `ArbitratorPromptEnvelopeVO(systemPrompt + userPrompt)`。
- 业务职责收敛：
  - `DebateService` 仅维护结构化仲裁上下文，不再拼装仲裁文本。
  - `LlmArbitrationDecisionStrategy` 仅负责调用与解析，不再负责“替换/追加”拼装策略。
- 日志降噪策略：
  - `INFO` 仅保留摘要：`ARBITRATOR_PROMPT_READY`
  - 全量候选集、偏好列表、动态指令全文降级为 `DEBUG`
  - 禁止在 `INFO` 打印超长 prompt 正文
