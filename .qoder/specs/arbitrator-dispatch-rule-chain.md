# Arbitrator (仲裁者) + Dispatch Rule Chain 实现方案

## Context

当前 `RoomDispatchService.dispatchNextSpeaker()` 中的调度逻辑是硬编码的 if/else（@指定 > 概率回复），无法扩展。项目路线图 M2 要求引入"仲裁者"角色——一个特殊的 AI 驱动 CLIENT，能通过 LLM 智能决策谁来发言。

本方案的目标：
1. 将硬编码调度逻辑重构为 **规则链**（Chain of Responsibility），支持插件化扩展
2. 引入 **仲裁者机制**，通过 `/arbitrate` 指令动态启用/关闭
3. 引入 **指令处理系统**，拦截 `/` 前缀消息并执行系统指令

---

## 一、规则链架构 (DispatchRuleChain)

### 1.1 核心值对象

**`DispatchContext`** — 调度上下文（不可变输入）:
```java
// com.dasi.domain.room.model.valobj.DispatchContext
- roomId: String
- triggerEventType: String  // USER_MSG 或 CLIENT_MSG_END
- currentSenderId: String   // 当前发言者
- atMemberIds: Set<String>  // 被 @ 的成员
- candidateClients: List<AiChatRoomMemberEntity>  // 候选 CLIENT（已排除当前发言者）
- arbitrationState: RoomArbitrationState  // 仲裁状态（可为 null）
```

**`DispatchDecision`** — 调度决策（输出）:
```java
// com.dasi.domain.room.model.valobj.DispatchDecision
- clientId: String
- reason: String  // "AT_MENTION" / "ARBITRATOR" / "PROBABILITY"
```

**`RoomArbitrationState`** — 仲裁状态值对象:
```java
// com.dasi.domain.room.model.valobj.RoomArbitrationState
- enabled: boolean
- arbitratorClientId: String
- roomId: String
+ static fromPublicData(String roomId, String json): RoomArbitrationState
+ toPublicDataJson(): String
+ static disabled(String roomId): RoomArbitrationState
```

### 1.2 规则接口与链

**规则接口** `IDispatchRule`:
```java
// com.dasi.domain.room.service.dispatch.IDispatchRule
public interface IDispatchRule {
    /**
     * 评估调度决策
     * @return RuleResult 包含本规则产生的决策和是否继续链路
     */
    RuleResult evaluate(DispatchContext context);
}
```

**规则结果** `RuleResult`:
```java
// com.dasi.domain.room.model.valobj.RuleResult
- decisions: List<DispatchDecision>  // 本规则产生的决策
- continueChain: boolean             // 是否继续执行下一个规则
- remainingCandidates: List<AiChatRoomMemberEntity>  // 剩余候选者（已移除已决策的）
```

**规则链** `DispatchRuleChain`（手动注册顺序，与项目现有风格一致，不用 @Order）:
```java
// com.dasi.domain.room.service.dispatch.DispatchRuleChain
@Component
public class DispatchRuleChain {
    private final List<IDispatchRule> rules;
    
    public DispatchRuleChain(AtMentionRule atRule, ArbitratorRule arbRule, ProbabilityRule probRule) {
        this.rules = List.of(atRule, arbRule, probRule);  // 显式顺序
    }
    
    public List<DispatchDecision> evaluate(DispatchContext context) {
        List<DispatchDecision> allDecisions = new ArrayList<>();
        DispatchContext current = context;
        
        for (IDispatchRule rule : rules) {
            RuleResult result = rule.evaluate(current);
            allDecisions.addAll(result.getDecisions());
            
            if (!result.isContinueChain()) break;
            
            // 用剩余候选者构建下一轮 context
            current = current.withCandidates(result.getRemainingCandidates());
        }
        return allDecisions;
    }
}
```

### 1.3 三条规则实现

| 规则 | 优先级 | 行为 | continueChain |
|------|--------|------|---------------|
| `AtMentionRule` | 1 | 从 atMemberIds 中匹配候选 CLIENT，产生决策，**移除已决策者后继续链路** | `true` |
| `ArbitratorRule` | 2 | 检查仲裁状态：若未启用则直接 pass；若启用且事件为 USER_MSG，调用仲裁者 LLM 决策；若启用且事件为 CLIENT_MSG_END，返回空（阻断链路） | 启用时 `false`，未启用时 `true` |
| `ProbabilityRule` | 3 | 对剩余候选者执行 50% 概率判定 | `false`（末尾规则） |

**关键设计决策**: 
- `AtMentionRule` 在所有模式下均生效（@ 指定的优先级最高）
- `ArbitratorRule` 启用时是终结规则（不会到 ProbabilityRule）
- 仲裁者仅在 `USER_MSG` 事件时调用 LLM；`CLIENT_MSG_END` 事件时返回空决策并阻断链路（防止连锁反应导致重复 LLM 调用）

---

## 二、仲裁者机制

### 2.1 仲裁者身份

仲裁者是一个**已加入房间的普通 CLIENT**，通过 `/arbitrate on <clientId>` 指令被临时授予仲裁者角色。仲裁者同时具备两个能力：
- **调度能力**: 分析上下文后输出结构化 JSON 决定谁发言（内部隐藏，不产生可见消息）
- **参与能力**: 被 @ 时作为普通 CLIENT 发言（走 AtMentionRule）

### 2.2 状态存储

利用现有 `ai_chat_room_state` 表的 `public_data` JSON 字段，**零 DDL 变更**：
```json
{
  "arbitration": {
    "enabled": true,
    "arbitratorClientId": "client_xxx"
  }
}
```

缓存 key: `room_arbitration:{roomId}`，变更时主动失效。

### 2.3 仲裁者 LLM 调用流程 (ArbitratorRule 内部)

```
1. 从 ApplicationContext 获取仲裁者的 ChatClient Bean
   beanName = AiType.CLIENT.getBeanName(arbitratorClientId)
   若 Bean 不存在，调用 aiDispatchService.dispatchArmoryStrategy() 装配

2. 构建调度专用 Prompt（非群聊 Prompt）
   - 加载 arbitrator-dispatch-template.txt 模板
   - 注入 {candidateList}：候选 CLIENT 列表 "[clientId: clientName, ...]"
   - 注入 {conversationHistory}：最近消息摘要（复用 queryContextMessages）

3. 调用 LLM：chatClient.prompt(new Prompt(messages)).call().content()

4. 解析响应 JSON：{"speakers": ["client_001"], "reasoning": "..."}
   - 解析失败时 log.warn 并返回空决策（防御性设计，不崩溃）

5. 产生 DispatchDecision 列表
```

### 2.4 仲裁者 Prompt 模板

新文件: `ai-agent-app/src/main/resources/prompt/system-prompt/arbitrator-dispatch-template.txt`

```
你是一个群聊对话仲裁者。你的职责是根据当前对话上下文，决定接下来哪些参与者应该发言。

## 当前候选参与者
{candidateList}

## 最近对话记录
{conversationHistory}

## 输出要求
请只输出一个合法的 JSON 对象，格式如下：
{"speakers": ["参与者ID1", "参与者ID2"], "reasoning": "你的判断依据"}

决策原则：
1. 选择 1-2 个与当前话题最相关的参与者
2. 考虑对话平衡性，避免总是选同一个参与者
3. 若有人被间接提及，优先选择该参与者
4. speakers 数组中只能包含上方候选列表中的 ID
```

---

## 三、指令处理系统

### 3.1 架构设计（策略模式）

```
com.dasi.domain.room.service.command/
├── IRoomCommandService.java           # 指令服务接口
├── RoomCommandService.java            # @Service 实现
└── handler/
    ├── ICommandHandler.java           # 单条指令处理器接口
    └── ArbitrateCommandHandler.java   # /arbitrate 处理器
```

**`IRoomCommandService`**:
```java
public interface IRoomCommandService {
    /** 尝试执行指令。返回 true 表示消息已被消费为指令，不再做普通消息处理 */
    boolean tryExecuteCommand(String roomId, String username, String content);
}
```

**`ICommandHandler`**:
```java
public interface ICommandHandler {
    String commandName();  // 返回 "arbitrate"
    void handle(String roomId, String username, String[] args);
}
```

**`RoomCommandService`**: 使用 `Map<String, ICommandHandler>` 注入所有处理器，通过 `commandName()` 匹配路由。

### 3.2 指令定义

| 指令 | 格式 | 行为 |
|------|------|------|
| 开启仲裁 | `/arbitrate on <clientId>` | 校验 clientId 存在 → 初始化/更新状态 → 广播 SYSTEM_NOTICE |
| 关闭仲裁 | `/arbitrate off` | 清除状态 → 广播 SYSTEM_NOTICE |
| 查询状态 | `/arbitrate status` | 查询并广播当前仲裁状态 |

### 3.3 集成点：RoomDispatchService.onMessage() 改造

```java
public void onMessage(String roomId, String username, String payload) {
    JSONObject json = JSON.parseObject(payload);
    String content = json.getString("content");
    
    // [新增] 指令拦截：以 "/" 开头的消息优先走指令处理
    if (content != null && content.startsWith("/")) {
        if (roomCommandService.tryExecuteCommand(roomId, username, content)) {
            return;  // 指令已消费，跳过普通消息处理
        }
    }
    
    // 原有逻辑不变...
    roomChatService.onUserMessage(request);
}
```

---

## 四、仓储层扩展

### 4.1 IChatRoomRepository 新增方法

```java
// 查询房间仲裁状态（带 Redis 缓存）
RoomArbitrationState queryArbitrationState(String roomId);

// 更新仲裁状态（乐观锁 + 缓存失效）
boolean updateArbitrationState(String roomId, RoomArbitrationState state);

// 初始化房间状态行（若不存在则 insert，存在则忽略）
void initRoomStateIfAbsent(String roomId);
```

### 4.2 ChatRoomRepository 实现

- 注入 `IAiChatRoomStateDao`（已有）
- `queryArbitrationState()`: Redis 缓存 `room_arbitration:{roomId}` → DB fallback → 解析 `publicData` JSON
- `updateArbitrationState()`: 查询当前 version → 序列化 state → `updateStateWithLock()` → 清 Redis
- `initRoomStateIfAbsent()`: 查询是否存在 → 不存在则 `insert`（currentStage="FREE", version=0）

---

## 五、RoomDispatchService 重构

### 5.1 重构后的 dispatchNextSpeaker()

```java
public void dispatchNextSpeaker(WebSocketEvent<?> wsEvent) {
    String eventType = wsEvent.getEventType();
    String roomId = wsEvent.getRoomId();
    
    // 守卫条件（不变）
    if (!USER_MSG.equals(eventType) && !CLIENT_MSG_END.equals(eventType)) return;
    
    // Phase 1: 构建调度上下文
    String currentSenderId = extractSenderId(wsEvent);
    Set<String> atMemberIds = extractAtMemberIds(wsEvent);
    List<AiChatRoomMemberEntity> candidates = chatRoomRepository.queryClientsByRoomId(roomId);
    candidates = candidates.stream()
        .filter(c -> !c.getMemberId().equals(currentSenderId))
        .collect(Collectors.toList());
    
    RoomArbitrationState arbState = chatRoomRepository.queryArbitrationState(roomId);
    
    DispatchContext context = DispatchContext.builder()
        .roomId(roomId)
        .triggerEventType(eventType)
        .currentSenderId(currentSenderId)
        .atMemberIds(atMemberIds)
        .candidateClients(candidates)
        .arbitrationState(arbState)
        .build();
    
    // Phase 2: 规则链评估
    List<DispatchDecision> decisions = dispatchRuleChain.evaluate(context);
    
    // Phase 3: 执行决策
    for (DispatchDecision decision : decisions) {
        String clientId = decision.getClientId();
        ensureBeanLoaded(clientId);
        roomChatService.clientChat(roomId, clientId);
    }
}
```

---

## 六、完整文件清单

### 新增文件（Domain 层）

| 文件路径 | 说明 |
|----------|------|
| `domain/room/model/valobj/DispatchContext.java` | 调度上下文值对象 |
| `domain/room/model/valobj/DispatchDecision.java` | 调度决策值对象 |
| `domain/room/model/valobj/RuleResult.java` | 规则执行结果 |
| `domain/room/model/valobj/RoomArbitrationState.java` | 仲裁状态值对象 |
| `domain/room/model/valobj/SystemNoticePayload.java` | 系统通知负载 |
| `domain/room/service/dispatch/IDispatchRule.java` | 规则接口 |
| `domain/room/service/dispatch/DispatchRuleChain.java` | 规则链编排器 |
| `domain/room/service/dispatch/rule/AtMentionRule.java` | @指定规则 |
| `domain/room/service/dispatch/rule/ArbitratorRule.java` | 仲裁者规则 |
| `domain/room/service/dispatch/rule/ProbabilityRule.java` | 概率规则 |
| `domain/room/service/command/IRoomCommandService.java` | 指令服务接口 |
| `domain/room/service/command/RoomCommandService.java` | 指令服务实现 |
| `domain/room/service/command/handler/ICommandHandler.java` | 指令处理器接口 |
| `domain/room/service/command/handler/ArbitrateCommandHandler.java` | /arbitrate 处理器 |

### 新增文件（资源）

| 文件路径 | 说明 |
|----------|------|
| `ai-agent-app/src/main/resources/prompt/system-prompt/arbitrator-dispatch-template.txt` | 仲裁者调度 Prompt |

### 修改文件

| 文件路径 | 变更内容 |
|----------|----------|
| `domain/room/service/chat/RoomDispatchService.java` | 注入 DispatchRuleChain + IRoomCommandService，重构 dispatchNextSpeaker() 和 onMessage() |
| `domain/room/apapter/repository/IChatRoomRepository.java` | 新增 3 个方法签名 |
| `infrastructure/repository/ChatRoomRepository.java` | 实现仲裁状态 CRUD，注入 IAiChatRoomStateDao |

### 无需变更

- 数据库 DDL（利用现有 `ai_chat_room_state.public_data`）
- MyBatis mapper XML（已有所需操作）
- Trigger 层（WebSocket handler / Listener 无需变更）
- 前端（SYSTEM_NOTICE 事件已有处理通道）

---

## 七、数据流图

### 自由聊天模式（与现有行为完全一致）

```
User 发消息 → onMessage() → [非指令] → onUserMessage() → publish(USER_MSG)
  → RoomAgentListener → dispatchNextSpeaker()
  → DispatchRuleChain.evaluate()
    → AtMentionRule: 提取 @成员决策 → continueChain=true
    → ArbitratorRule: 状态未启用 → continueChain=true
    → ProbabilityRule: 50%概率 → 产生决策
  → 执行决策: clientChat() → publish(CLIENT_MSG + CLIENT_MSG_END)
  → 闭环: CLIENT_MSG_END → dispatchNextSpeaker() → ...
```

### 仲裁模式

```
User 发消息 → onMessage() → [非指令] → onUserMessage() → publish(USER_MSG)
  → RoomAgentListener → dispatchNextSpeaker()
  → DispatchRuleChain.evaluate()
    → AtMentionRule: 提取 @成员决策 → continueChain=true
    → ArbitratorRule: 状态已启用 + USER_MSG事件
       → 调用仲裁者 LLM → 解析 speakers → 产生决策
       → continueChain=false (ProbabilityRule 不执行)
  → 执行决策: 仅被仲裁者指定的 CLIENT 发言

Client 回复完毕 → CLIENT_MSG_END → dispatchNextSpeaker()
  → DispatchRuleChain.evaluate()
    → AtMentionRule: 无 @ → 继续
    → ArbitratorRule: 状态已启用 + CLIENT_MSG_END事件
       → 返回空决策 + continueChain=false（不再触发更多发言）
  → 无决策执行（本轮结束）
```

### 模式切换

```
User 发送 "/arbitrate on client_001"
  → onMessage() → 检测 "/" 前缀 → tryExecuteCommand()
  → ArbitrateCommandHandler.handle()
    → 校验 client_001 在房间内
    → initRoomStateIfAbsent(roomId)
    → updateArbitrationState(enabled=true, arbitratorClientId="client_001")
    → publish SYSTEM_NOTICE: "仲裁模式已开启，仲裁者: [xxx(client_001)]"
  → 返回 true（消息不做普通处理）
```

---

## 八、风险与防御

| 风险 | 防御措施 |
|------|----------|
| 仲裁者 LLM 返回非法 JSON | try-catch 包裹解析，失败时 log.warn + 返回空决策（本轮无人发言，不崩溃） |
| 仲裁者 LLM 调用超时 | 与现有 clientChat 共享超时机制，单点失败不影响系统 |
| 乐观锁冲突（并发更新状态） | updateArbitrationState 返回 boolean，失败时通知用户重试 |
| 仲裁者 CLIENT 被移出房间 | ArbitratorRule 检查 Bean 存在性，不存在时自动降级为 ProbabilityRule（continueChain=true） |
| 无限链路调用 | CLIENT_MSG_END 在仲裁模式下阻断链路（返回空+false） |

---

## 九、实现顺序

1. **值对象层**: DispatchContext, DispatchDecision, RuleResult, RoomArbitrationState, SystemNoticePayload
2. **仓储扩展**: IChatRoomRepository 接口 + ChatRoomRepository 实现
3. **规则链核心**: IDispatchRule + DispatchRuleChain + AtMentionRule + ProbabilityRule
4. **RoomDispatchService 重构**: 使用规则链替换硬编码（此时应可编译+逻辑等价）
5. **指令系统**: IRoomCommandService + RoomCommandService + ICommandHandler + ArbitrateCommandHandler
6. **仲裁者规则**: ArbitratorRule + Prompt 模板
7. **集成测试验证**

---

## 十、验证方案

1. **编译验证**: `mvn compile -pl ai-agent-domain,ai-agent-infrastructure,ai-agent-app,ai-agent-trigger -am` 确认无编译错误
2. **自由聊天回归**: 不发送任何 `/arbitrate` 指令，验证 @指定和概率回复行为与重构前完全一致
3. **指令系统**: 发送 `/arbitrate on <clientId>` 验证 SYSTEM_NOTICE 广播正确，`/arbitrate status` 返回状态
4. **仲裁模式**: 开启仲裁后发送普通消息，验证仅仲裁者指定的 CLIENT 回复，其他 CLIENT 沉默
5. **@ 穿透**: 仲裁模式下 @ 某个未被仲裁者选中的 CLIENT，验证该 CLIENT 仍然回复
6. **模式关闭**: `/arbitrate off` 后验证恢复自由聊天模式
