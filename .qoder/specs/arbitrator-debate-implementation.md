# 仲裁者自动辩论赛 - 实现规划 v2

## Context

当前 `RoomDispatchService.dispatchNextSpeaker()` 中的调度逻辑是硬编码的 if/else（@指定 > 概率回复），无法扩展。需要引入"仲裁者"角色（一个 CLIENT AI），作为**主持人**通过 LLM 智能决策谁来发言并给出简短解释，实现简化赛制的自动辩论赛。

### 核心设计原则
1. **REST API 驱动** — 仲裁者指定、辩论管理全部通过接口完成，不引入聊天指令系统
2. **仲裁者 = 主持人** — 只负责选下一个说话者 + 提供简短场景解释(≤50字)，不评分不判胜负
3. **简化赛制** — 每轮固定对话次数，仲裁者自主调度正反方，用户决定每轮胜方
4. **同一辩题多轮** — 一个 session 支持多轮辩论，用户宣布胜方后可继续下一轮

---

## 一、数据库表设计

### 表 1: `ai_debate_session` (辩论会话表)

```sql
CREATE TABLE ai_debate_session (
  id                   bigint       PRIMARY KEY AUTO_INCREMENT,
  session_id           varchar(64)  NOT NULL COMMENT '辩论会话业务ID (debate_xxx)',
  room_id              varchar(64)  NOT NULL COMMENT '关联房间ID',
  topic                varchar(512) NOT NULL COMMENT '辩论主题',
  arbitrator_client_id varchar(64)  NOT NULL COMMENT '仲裁者(主持人) CLIENT ID',
  pro_client_ids       varchar(512) NOT NULL COMMENT '正方CLIENT ID列表(逗号分隔)',
  con_client_ids       varchar(512) NOT NULL COMMENT '反方CLIENT ID列表(逗号分隔)',
  turns_per_round      int          NOT NULL DEFAULT 6  COMMENT '每轮固定对话次数',
  current_round        int          NOT NULL DEFAULT 0  COMMENT '当前轮次号(0=未开始)',
  current_turn         int          NOT NULL DEFAULT 0  COMMENT '当前轮内对话序号(0=未开始)',
  round_winners        varchar(255) DEFAULT NULL COMMENT '每轮胜方记录(逗号分隔: PRO,CON,...)',
  status               varchar(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/ROUND_END/FINISHED',
  version              int          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  is_deleted           tinyint      DEFAULT 0,
  create_time          datetime     DEFAULT CURRENT_TIMESTAMP,
  update_time          datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_session_id (session_id),
  INDEX idx_room_status (room_id, status),
  INDEX idx_room_create (room_id, create_time)
) COMMENT='辩论会话表';
```

**索引说明**:
- `uk_session_id` — 业务ID唯一，精确查询
- `idx_room_status` — 高频：查房间活跃辩论 `WHERE room_id=? AND status='RUNNING'`
- `idx_room_create` — 历史辩论列表

**状态机**:
```
PENDING → RUNNING → ROUND_END → RUNNING → ... → FINISHED
                                                    ↑
                                               (API 手动结束)
```

### 表 2: `ai_debate_record` (辩论对话记录表)

记录每一次发言，打包给仲裁者 CLIENT 作为决策上下文。

```sql
CREATE TABLE ai_debate_record (
  id                   bigint       PRIMARY KEY AUTO_INCREMENT,
  record_id            varchar(64)  NOT NULL COMMENT '记录业务ID (dr_xxx)',
  session_id           varchar(64)  NOT NULL COMMENT '关联辩论会话ID',
  room_id              varchar(64)  NOT NULL COMMENT '冗余房间ID',
  round_number         int          NOT NULL COMMENT '所属轮次(1-based)',
  turn_number          int          NOT NULL COMMENT '轮内对话序号(1-based)',
  speaker_client_id    varchar(64)  NOT NULL COMMENT '发言者CLIENT ID',
  speaker_name         varchar(64)  NULL COMMENT '发言者昵称(冗余)',
  side                 varchar(10)  NOT NULL COMMENT '阵营: PRO / CON',
  message_id           varchar(64)  NULL COMMENT '关联 ai_chat_room_message.message_id',
  arbitrator_reasoning varchar(100) NOT NULL COMMENT '仲裁者选择理由(限50字)',
  is_deleted           tinyint      DEFAULT 0,
  create_time          datetime     DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_record_id (record_id),
  INDEX idx_session_round_turn (session_id, round_number, turn_number),
  INDEX idx_room_create (room_id, create_time)
) COMMENT='辩论对话记录表';
```

**索引说明**:
- `uk_record_id` — 业务ID唯一
- `idx_session_round_turn` — 核心查询：按会话+轮次查记录，构建仲裁者上下文
- `idx_room_create` — 按房间时间线查询

---

## 二、REST API 设计

遵循现有 `/chat-room/` 路径前缀约定，新增以下端点：

### 2.1 仲裁者管理

| 方法 | 路径 | 请求体 | 响应 | 说明 |
|------|------|--------|------|------|
| POST | `/chat-room/arbitrator/set` | `{roomId, clientId}` | `Result<Boolean>` | 指定房间仲裁者 |
| DELETE | `/chat-room/arbitrator/remove` | param: `roomId` | `Result<Boolean>` | 移除仲裁者 |

### 2.2 辩论赛管理

| 方法 | 路径 | 请求体 | 响应 | 说明 |
|------|------|--------|------|------|
| POST | `/chat-room/debate/start` | `DebateStartRequest` | `Result<String>` sessionId | 开始辩论(自动开始第一轮) |
| POST | `/chat-room/debate/round/winner` | `{roomId, winnerSide}` | `Result<Boolean>` | 宣布当前轮胜方 |
| POST | `/chat-room/debate/round/next` | `{roomId}` | `Result<Boolean>` | 开始下一轮 |
| POST | `/chat-room/debate/stop` | `{roomId}` | `Result<Boolean>` | 手动结束辩论 |
| GET | `/chat-room/debate/status` | param: `roomId` | `Result<DebateStatusDTO>` | 查询辩论状态 |

### 2.3 请求/响应 DTO

```java
// DebateStartRequest
{
  roomId: String,           // 房间ID
  topic: String,            // 辩论主题
  proClientIds: List<String>, // 正方CLIENT列表(用户指定)
  conClientIds: List<String>, // 反方CLIENT列表(用户指定)
  turnsPerRound: Integer    // 每轮对话次数(可选，默认6)
}

// DebateWinnerRequest
{
  roomId: String,
  winnerSide: String        // "PRO" 或 "CON"
}

// DebateStatusDTO
{
  sessionId: String,
  topic: String,
  status: String,           // PENDING/RUNNING/ROUND_END/FINISHED
  currentRound: Integer,
  currentTurn: Integer,
  turnsPerRound: Integer,
  roundWinners: List<String>, // ["PRO", "CON", ...]
  proMembers: List<{clientId, clientName}>,
  conMembers: List<{clientId, clientName}>,
  arbitratorName: String
}
```

---

## 三、辩论赛完整数据流

### 3.1 开始辩论

```
用户 → POST /chat-room/debate/start {roomId, topic, proIds, conIds}
  → ChatRoomController → IDebateService.startDebate()
    → 1. 校验: 房间有仲裁者(已通过 /arbitrator/set 设置)
    → 2. 校验: proIds, conIds 都在房间内，不重叠
    → 3. 创建 DebateSession(status=RUNNING, currentRound=1, currentTurn=0)
    → 4. 持久化 ai_debate_session
    → 5. 更新仲裁状态: arbitrationState.debateSessionId = sessionId
    → 6. 广播 SYSTEM_NOTICE(DEBATE_START): "辩论开始！主题：xxx，正方：[A,B]，反方：[C,D]"
    → 7. 触发首次调度: 仲裁者 LLM → 选定 speaker1 + reasoning
    → 8. 广播 SYSTEM_NOTICE(HOST_INTRO): reasoning (主持人解说)
    → 9. clientChat(roomId, speaker1)
```

### 3.2 轮内自动循环 (核心调度循环)

```
Speaker1 回复完毕 → CLIENT_MSG_END
  → RoomAgentListener → dispatchNextSpeaker()
  → DispatchRuleChain.evaluate(context)
    → AtMentionRule: 无@ → pass
    → ArbitratorRule: 辩论模式 + CLIENT_MSG_END
      → 1. 加载 DebateSession
      → 2. 记录本次发言: 保存 DebateRecord(turn=currentTurn)
      → 3. session.currentTurn++
      → 4. 检查: currentTurn >= turnsPerRound?
         → YES: session.status = ROUND_END
                广播 SYSTEM_NOTICE(ROUND_END): "第N轮辩论结束，请宣布胜方"
                return 空决策 (等待用户 API 调用)
         → NO:  仲裁者 LLM → 选定下一个 speaker + reasoning
                广播 SYSTEM_NOTICE(HOST_INTRO): reasoning
                return DispatchDecision(speaker, "DEBATE_ARBITRATOR")
    → ProbabilityRule: 被跳过 (continueChain=false)
  → 执行决策: clientChat(roomId, nextSpeaker)
  → 循环...
```

### 3.3 宣布胜方 + 下一轮

```
用户 → POST /chat-room/debate/round/winner {roomId, winnerSide: "PRO"}
  → IDebateService.declareRoundWinner()
    → 1. 校验: session.status == ROUND_END
    → 2. 追加 roundWinners: "PRO"
    → 3. 持久化更新 (乐观锁)
    → 4. 广播 SYSTEM_NOTICE(WINNER): "第N轮胜方：正方"

用户 → POST /chat-room/debate/round/next {roomId}
  → IDebateService.startNextRound()
    → 1. session.currentRound++, currentTurn=0, status=RUNNING
    → 2. 持久化更新
    → 3. 广播 SYSTEM_NOTICE(ROUND_START): "第N+1轮辩论开始"
    → 4. 触发仲裁者 LLM → 选定首位发言者
    → 5. 广播 HOST_INTRO + clientChat()
```

### 3.4 结束辩论

```
用户 → POST /chat-room/debate/stop {roomId}
  → IDebateService.stopDebate()
    → 1. session.status = FINISHED
    → 2. 清除仲裁状态: arbitrationState.debateSessionId = null
    → 3. 清除 Redis 缓存
    → 4. 广播 SYSTEM_NOTICE(DEBATE_END): "辩论结束！共N轮，胜负记录：..."
    → 5. 房间恢复自由聊天模式
```

---

## 四、分阶段实现计划

### 阶段 1: 数据基础层

**目标**: 建立辩论赛的完整数据存储链路。

#### 1.1 DDL
- `docs/mysql/table-struction.sql` — 追加 `ai_debate_session` + `ai_debate_record`

#### 1.2 基础设施层 (Infrastructure)
| 文件 | 路径前缀: `infrastructure/` | 说明 |
|------|------|------|
| `AiDebateSession.java` | `persistent/po/` | 会话 PO |
| `AiDebateRecord.java` | `persistent/po/` | 记录 PO |
| `IAiDebateSessionDao.java` | `persistent/dao/` | 会话 DAO (MyBatis Mapper接口) |
| `IAiDebateRecordDao.java` | `persistent/dao/` | 记录 DAO |
| `ai-debate-session-mapper.xml` | `resources/mapper/` | 会话 SQL |
| `ai-debate-record-mapper.xml` | `resources/mapper/` | 记录 SQL |
| `DebateRepository.java` | `repository/` | 仓储实现 (extends AbstractRepository) |

**DAO 核心方法**:
```java
// IAiDebateSessionDao
void insert(AiDebateSession session);
AiDebateSession queryBySessionId(String sessionId);
AiDebateSession queryActiveByRoomId(String roomId);  // status IN ('RUNNING','ROUND_END')
int updateWithLock(AiDebateSession session);          // 乐观锁更新
void deleteBySessionId(String sessionId);

// IAiDebateRecordDao
void insert(AiDebateRecord record);
List<AiDebateRecord> queryBySessionAndRound(String sessionId, int roundNumber);
List<AiDebateRecord> queryBySessionId(String sessionId);  // 全部记录
void deleteBySessionId(String sessionId);
```

#### 1.3 领域层 (Domain)
| 文件 | 路径前缀: `domain/room/` | 说明 |
|------|------|------|
| `DebateSessionEntity.java` | `model/entity/` | 辩论会话实体 |
| `DebateRecordEntity.java` | `model/entity/` | 辩论记录实体 |
| `DebateStatus.java` | `model/valobj/` | 状态枚举 |
| `IDebateRepository.java` | `apapter/repository/` | 仓储接口 |

**DebateSessionEntity 关键设计**:
```java
@Data @Builder
public class DebateSessionEntity {
    private String sessionId;
    private String roomId;
    private String topic;
    private String arbitratorClientId;
    private List<String> proClientIds;   // Java List ↔ DB逗号分隔
    private List<String> conClientIds;
    private int turnsPerRound;
    private int currentRound;
    private int currentTurn;
    private List<String> roundWinners;   // Java List ↔ DB逗号分隔
    private DebateStatus status;
    private int version;

    // 领域方法
    public String getSideForClient(String clientId);  // → "PRO"/"CON"/null
    public List<String> getAllDebaterIds();            // pro + con 合并
    public boolean isRoundComplete();                 // currentTurn >= turnsPerRound
    public void advanceTurn();                        // currentTurn++
    public void advanceRound();                       // currentRound++, currentTurn=0
}
```

**IDebateRepository 接口**:
```java
void saveSession(DebateSessionEntity session);
boolean updateSession(DebateSessionEntity session);    // 乐观锁, 返回成功/失败
DebateSessionEntity queryActiveByRoomId(String roomId);
DebateSessionEntity queryBySessionId(String sessionId);
void saveRecord(DebateRecordEntity record);
List<DebateRecordEntity> queryRecordsByRound(String sessionId, int roundNumber);
List<DebateRecordEntity> queryAllRecords(String sessionId);
```

---

### 阶段 2: 规则链重构

**目标**: 将硬编码 if/else 替换为规则链，为辩论模式接入做准备。

#### 2.1 规则链核心
| 文件 | 路径前缀: `domain/room/service/dispatch/` | 说明 |
|------|------|------|
| `IDispatchRule.java` | 根目录 | 规则接口 |
| `DispatchRuleChain.java` | 根目录 | 规则链编排器 |
| `AtMentionRule.java` | `rule/` | @指定规则 |
| `ProbabilityRule.java` | `rule/` | 概率规则 |
| `ArbitratorRule.java` | `rule/` | 仲裁者规则(骨架) |

#### 2.2 仲裁状态
- `RoomArbitrationState` 扩展 `debateSessionId` 字段
- `IChatRoomRepository` 新增:
  - `queryArbitrationState(String roomId)` — 缓存 `room_arbitration:{roomId}`
  - `updateArbitrationState(String roomId, RoomArbitrationState state)` — 乐观锁 + 缓存失效
  - `initRoomStateIfAbsent(String roomId)` — 初始化 state 行
- `ChatRoomRepository` 实现: 读写 `ai_chat_room_state.public_data` JSON

#### 2.3 重构 RoomDispatchService
- `dispatchNextSpeaker()` → `DispatchRuleChain.evaluate(context)` → 执行决策列表
- `DispatchContext` 新增 `debateSession` 可选字段

**验证点**: 重构后 @指定 + 50%概率 行为与原逻辑完全一致。

---

### 阶段 3: REST API + 辩论服务

**目标**: 实现 API 端点和辩论领域服务的核心逻辑（不含 LLM 调用）。

#### 3.1 API 层 (ai-agent-api + ai-agent-trigger)

**新增 DTO**:
| 文件 | 路径: `api/dto/` | 说明 |
|------|------|------|
| `DebateStartRequest.java` | `request/` | 开始辩论请求 |
| `DebateWinnerRequest.java` | `request/` | 宣布胜方请求 |
| `DebateRoomRequest.java` | `request/` | 通用辩论房间请求(roomId) |
| `ArbitratorSetRequest.java` | `request/` | 设置仲裁者请求 |
| `DebateStatusDTO.java` | `response/` | 辩论状态响应 |

**API 接口** — 扩展 `IChatRoomService`:
```java
// 仲裁者
@PostMapping("/chat-room/arbitrator/set")
Result<Boolean> setArbitrator(@RequestBody ArbitratorSetRequest request);

@DeleteMapping("/chat-room/arbitrator/remove")
Result<Boolean> removeArbitrator(@RequestParam("roomId") String roomId);

// 辩论赛
@PostMapping("/chat-room/debate/start")
Result<String> startDebate(@RequestBody DebateStartRequest request);

@PostMapping("/chat-room/debate/round/winner")
Result<Boolean> declareWinner(@RequestBody DebateWinnerRequest request);

@PostMapping("/chat-room/debate/round/next")
Result<Boolean> startNextRound(@RequestBody DebateRoomRequest request);

@PostMapping("/chat-room/debate/stop")
Result<Boolean> stopDebate(@RequestBody DebateRoomRequest request);

@GetMapping("/chat-room/debate/status")
Result<DebateStatusDTO> queryDebateStatus(@RequestParam("roomId") String roomId);
```

**Controller** — 在 `ChatRoomController` 中实现新接口方法。

#### 3.2 辩论领域服务

| 文件 | 路径: `domain/room/service/debate/` | 说明 |
|------|------|------|
| `IDebateService.java` | 接口 | 辩论服务接口 |
| `DebateService.java` | 实现 | 辩论服务实现 |

**IDebateService 方法**:
```java
// 仲裁者管理
void setArbitrator(String roomId, String clientId);
void removeArbitrator(String roomId);

// 辩论生命周期
String startDebate(String roomId, String topic, List<String> proIds,
                   List<String> conIds, Integer turnsPerRound);
void declareRoundWinner(String roomId, String winnerSide);
void startNextRound(String roomId);
void stopDebate(String roomId);
DebateSessionEntity queryDebateStatus(String roomId);

// 内部方法(供 ArbitratorRule 调用)
void recordTurn(String sessionId, String speakerClientId, String messageId,
                String contentSummary, String arbitratorReasoning);
DebateSessionEntity advanceTurnAndCheck(String sessionId);
String buildDebateContext(String sessionId);
```

---

### 阶段 4: 仲裁者 LLM + 辩论调度

**目标**: 实现 ArbitratorRule 的 LLM 调度逻辑，完成辩论自动循环。

#### 4.1 Prompt 模板

| 文件 | 路径: `ai-agent-app/src/main/resources/prompt/system-prompt/` |
|------|------|
| `debate-dispatch-template.txt` | 辩论调度 Prompt |

```text
你是一场辩论赛的主持人。你的职责是根据辩论进展，选择下一位发言者。

## 辩论主题
{topic}

## 正方成员
{proCandidates}

## 反方成员
{conCandidates}

## 本轮已有发言记录
{roundHistory}

## 最近对话内容
{conversationHistory}

## 当前进度
第{currentRound}轮，第{currentTurn}/{turnsPerRound}次发言

## 输出要求
请只输出一个合法的 JSON 对象：
{"speaker": "参与者ID", "side": "PRO或CON", "reasoning": "选择理由(不超过50字)"}

决策原则：
1. 正反方交替发言，保持辩论平衡
2. 选择与当前论点最相关的参与者
3. 考虑对话平衡性，避免总是选同一个参与者
4. speaker 只能是上方正方或反方成员中的ID
```

#### 4.2 ArbitratorRule 辩论分支

```java
// ArbitratorRule.evaluate(context) 核心逻辑:

if (!arbitrationState.isEnabled()) {
    return RuleResult.passThrough(context);  // 未启用，走下一条规则
}

String debateSessionId = arbitrationState.getDebateSessionId();

if (debateSessionId == null) {
    // 自由仲裁模式 (预留，暂不实现)
    return RuleResult.passThrough(context);
}

// === 辩论模式 ===
DebateSessionEntity session = debateRepository.queryBySessionId(debateSessionId);

if (triggerEventType == CLIENT_MSG_END) {
    // 1. 记录上一次发言
    debateService.recordTurn(...);

    // 2. 推进进度
    session = debateService.advanceTurnAndCheck(debateSessionId);

    // 3. 本轮结束?
    if (session.getStatus() == ROUND_END) {
        publishSystemNotice("第X轮辩论结束，请宣布胜方");
        return RuleResult.terminate();  // 等待用户 API
    }
}

// 调用仲裁者 LLM 选人
DebateDispatchResult result = callArbitratorLLM(session, context);

// 广播主持人解说
publishSystemNotice(HOST_INTRO, result.reasoning);

// 返回调度决策
return RuleResult.of(
    DispatchDecision.of(result.speakerId, "DEBATE_ARBITRATOR"),
    false  // continueChain = false
);
```

#### 4.3 LLM 调用流程
1. 从 `ApplicationContext` 获取仲裁者 ChatClient Bean
2. 若 Bean 不存在 → `dispatchArmoryStrategy()` 装配
3. 调用 `debateService.buildDebateContext()` 组装 Prompt
4. LLM 调用 → 解析 JSON → `{"speaker": "xxx", "reasoning": "..."}`
5. **解析失败降级**: 轮替制（上次正方→这次反方，反之亦然）

---

### 阶段 5: 集成验证与防护

#### 5.1 防无限循环机制

| 防护层 | 机制 |
|--------|------|
| `turnsPerRound` 硬限制 | currentTurn >= turnsPerRound 时自动停止本轮 |
| ROUND_END 状态阻断 | 状态为 ROUND_END 时 ArbitratorRule 不产生决策 |
| CLIENT_MSG_END 单次决策 | 每次 CLIENT_MSG_END 仅选 1 位发言者 |
| LLM 超时/失败降级 | 超时/解析失败 → 正反轮替制 |
| 事件类型守卫 | 仅 CLIENT_MSG_END 触发辩论调度 |

#### 5.2 编译验证
```bash
mvn compile -pl ai-agent-domain,ai-agent-infrastructure,ai-agent-app,ai-agent-trigger -am
```

#### 5.3 功能验证清单
- [ ] 自由聊天回归: 未设仲裁者时 @指定+概率回复不变
- [ ] `POST /arbitrator/set` → 设置仲裁者成功
- [ ] `POST /debate/start` → SYSTEM_NOTICE 广播 → 第一位发言者开始说话
- [ ] 辩论自动循环: 仲裁者选人 → 广播解说 → 发言 → 记录 → 选下一个
- [ ] turnsPerRound 达到 → 自动 ROUND_END → 广播提示
- [ ] `POST /debate/round/winner` → 记录胜方
- [ ] `POST /debate/round/next` → 开始下一轮 → 循环继续
- [ ] `POST /debate/stop` → 辩论结束 → 恢复自由聊天
- [ ] @ 穿透: 辩论中 @某人 → 该人优先发言(AtMentionRule 优先级最高)

---

## 五、完整文件清单

### 新增文件 (共 20 个)

**API 层** (5 个):
| 文件 | 路径: `ai-agent-api/src/.../api/dto/` |
|------|------|
| `DebateStartRequest.java` | `request/` |
| `DebateWinnerRequest.java` | `request/` |
| `DebateRoomRequest.java` | `request/` |
| `ArbitratorSetRequest.java` | `request/` |
| `DebateStatusDTO.java` | `response/` |

**Domain 层** (10 个):
| 文件 | 路径前缀: `domain/room/` |
|------|------|
| `model/entity/DebateSessionEntity.java` |
| `model/entity/DebateRecordEntity.java` |
| `model/valobj/DebateStatus.java` |
| `apapter/repository/IDebateRepository.java` |
| `service/debate/IDebateService.java` |
| `service/debate/DebateService.java` |
| `service/dispatch/IDispatchRule.java` |
| `service/dispatch/DispatchRuleChain.java` |
| `service/dispatch/rule/AtMentionRule.java` |
| `service/dispatch/rule/ArbitratorRule.java` |
| `service/dispatch/rule/ProbabilityRule.java` |

**Infrastructure 层** (5 个):
| 文件 | 路径前缀: `infrastructure/` |
|------|------|
| `persistent/po/AiDebateSession.java` |
| `persistent/po/AiDebateRecord.java` |
| `persistent/dao/IAiDebateSessionDao.java` |
| `persistent/dao/IAiDebateRecordDao.java` |
| `repository/DebateRepository.java` |

**资源文件** (3 个):
| 文件 | 路径 |
|------|------|
| `mapper/ai-debate-session-mapper.xml` |
| `mapper/ai-debate-record-mapper.xml` |
| `prompt/system-prompt/debate-dispatch-template.txt` |

### 修改文件 (7 个)

| 文件 | 变更 |
|------|------|
| `IChatRoomService.java` (api) | 新增 7 个辩论+仲裁者 API 方法签名 |
| `ChatRoomController.java` (trigger) | 实现新增的 API 方法 |
| `RoomDispatchService.java` (domain) | 注入规则链，重构 dispatchNextSpeaker() |
| `IChatRoomRepository.java` (domain) | 新增 3 个仲裁状态方法 |
| `ChatRoomRepository.java` (infra) | 实现仲裁状态 CRUD |
| `RoomArbitrationState.java` (domain) | 新增 debateSessionId 字段 |
| `table-struction.sql` (docs) | 追加 2 张新表 DDL |

---

## 六、Redis 缓存策略

| 缓存 Key | 内容 | 失效时机 |
|---|---|---|
| `room_arbitration:{roomId}` | RoomArbitrationState JSON | set/remove 仲裁者, debate start/stop |
| `debate_session:{roomId}` | 活跃 DebateSessionEntity | 每次 turn/round 推进, debate stop |
| `debate_records:{sessionId}:{roundNumber}` | 当前轮的 List\<DebateRecordEntity\> | 新 turn 记录时失效 |
