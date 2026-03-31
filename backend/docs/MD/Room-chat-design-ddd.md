# 多 Bot 群聊系统（DDD）设计说明与首版实现报告

> 目标：构建“多 Bot（Client/Agent）”智能聊天室。支持人机与机机协作，演进到辩论/仲裁/游戏等复杂场景。

## 1. 概述（Scope）

- 核心诉求
  - 在同一房间内，用户与多个 Bot 同时交流；Bot 之间可在策略控制下产生交互。
  - 通过“提示词工程 + 上下文装配 + 事件驱动”，保证多终端实时体验与可扩展性。

- 首版（MVP）交付范围
  - Bot（Client）可在群聊中概率性地参与回复（WebSocket 实时下行）。
  - 支持 USER/CLIENT/AGENT 加入房间；当前仅 **CLIENT** 参与概率回复；**AGENT** 允许入房但暂不参与回复（后续接入 execute 执行链）。
  - Client 的人设提示词可在“模型配置”中创建/更新/删除；列表可回显 `clientName + systemPrompt`。
  - 前端布局固定侧栏，聊天区域滚动；支持邀请 CLIENT/AGENT；气泡区分 USER/CLIENT/AGENT。

## 项目总览（Overview）

- 一句话目标：提供多 Bot（Client/Agent）协同的群聊空间，支持指定 @、概率回复、未来仲裁与回合推进。
- 典型场景：人机混聊、Bot 间协作、点名指定某个 Bot 回答、允许仲裁官控制发言权。
- 事件闭环：USER_MSG →（落库+广播）→ CLIENT_MSG → CLIENT_MSG_END（触发调度下一发言者）。
- 架构风格：Trigger 接入、Domain 领域服务、Armory 装配策略、Execute 执行链、Repository 映射。

## 快速上手（Quick Start）

- 后端 WS 路径：`/miniagent/api/v1/ws/room/{roomId}/{username}`（浏览器原生 WS，不携带 Authorization）
- 前端页面：进入 [RoomChat.vue](file:///f:/java/code/Agent/frontend/src/components/RoomChat.vue) 页面，连接房间并发送消息
- 指定 @：输入框键入 `@` 触发成员列表或右键消息选择 `@TA`，发送时自动携带 `atMemberIds`（逗号分隔的 memberId）
- 事件行为：被 @ 的成员强制回答；其它成员按概率参与；客户端回答结束触发 `CLIENT_MSG_END`


## 2. 术语与角色（Glossary）

- USER：人类用户（member_type='USER'），memberId=用户名（username）。
- Bot（Client）：可直接发言的对话客户端（member_type='CLIENT'），memberId=clientId。具备：模型信息 + 系统提示词（人设）。
- Bot（Agent）：工作流/角色（member_type='AGENT'），memberId=agentId。当前仅允许入房，**不参与首版回复**；未来通过 execute 执行链只渲染“最终总结”。
- 事件名（下行/内部信号，必须使用 **具体名** 便于排障）
  - 对外：`USER_MSG`、`CLIENT_MSG`（保留 `AGENT_MSG` 以便未来接入）
  - 对内信号：`CLIENT_MSG_END`（保留 `AGENT_MSG_END` 以便未来接入）


## 3. 系统架构（Architecture & Key Entrypoints）

- Trigger（接入层）
  - WebSocket 握手与路径解析（roomId、username）  
    `backend/ai-agent-app/src/main/java/com/dasi/config/WebSocketConfig.java`
  - 缓存失效切面（按类型清理 Redis 前缀）  
    `backend/ai-agent-app/src/main/java/com/dasi/aop/CacheEvictAspect.java`
  - 用户控制器（个人模型配置/列表/更新/删除）  
    `backend/ai-agent-trigger/src/main/java/com/dasi/trigger/controller/UserController.java`

- Domain（领域层）
  - 房间管理（创建/加入/离开/查询）  
    `backend/ai-agent-domain/src/main/java/com/dasi/domain/room/service/room/RoomAdminService.java`
  - 成员入场策略（策略模式实现不同成员入场逻辑）  
    `backend/ai-agent-domain/src/main/java/com/dasi/domain/room/service/room/impl/AbstractRoomMemberService.java`（基类）  
    `backend/ai-agent-domain/src/main/java/com/dasi/domain/room/service/room/impl/ClientMemberService.java`（提示词重构核心）
  - 调度与回复（概率触发、装配检查、指令下达）  
    `backend/ai-agent-domain/src/main/java/com/dasi/domain/room/service/chat/RoomDispatchService.java`
  - 对话执行（clientChat，同步调用 LLM，落库并广播）  
    `backend/ai-agent-domain/src/main/java/com/dasi/domain/room/service/chat/RoomChatService.java`
  - Armory 装配（Client/Agent Bean 装配，支持覆盖注册）  
    `backend/ai-agent-domain/src/main/java/com/dasi/domain/ai/service/armory/strategy/ArmoryChatStrategy.java`
  - 执行框架入口（未来对接 Agent 执行链，仅渲染最终总结）  
    `backend/ai-agent-domain/src/main/java/com/dasi/domain/ai/service/execute`

- Infrastructure（基础设施）
  - 房间仓储/成员映射/消息映射  
    `backend/ai-agent-infrastructure/src/main/java/com/dasi/infrastructure/repository/ChatRoomRepository.java`
  - AI 配置仓储（支持提示词更新与轻量级查询）  
    `backend/ai-agent-infrastructure/src/main/java/com/dasi/infrastructure/repository/AiRepository.java`
  - 缓存基类（支持过期时间与通用缓存处理）  
    `backend/ai-agent-infrastructure/src/main/java/com/dasi/infrastructure/repository/AbstractRepository.java`

- Frontend（关键页面）
  - 群聊页（邀请 CLIENT/AGENT、气泡渲染、WebSocket 事件处理）  
    `frontend/src/components/RoomChat.vue`
  - 模型配置页（clientName + systemPrompt 的创建/编辑/回填）  
    `frontend/src/components/Setting.vue`
  - 固定布局（左侧固定、右侧滚动容器）  
    `frontend/src/App.vue`、`frontend/src/style.css`


## 4. 数据模型与约定（Data Model）

- 房间与消息：`ai_chat_room / ai_chat_room_member / ai_chat_room_message / ai_chat_room_state`
  - 成员类型：`USER / CLIENT / AGENT`  
    - USER.memberId = username  
    - CLIENT.memberId = clientId  
    - AGENT.memberId = agentId
  - 成员入房冗余 `member_name`，用于渲染与上下文展示。
  - `agent_session_id`：对 CLIENT/AGENT 统一生成，用于未来私有上下文隔离。

- 模型/Client/Prompt：`ai_api / ai_model / ai_client / ai_config / ai_prompt`
  - **Prompt 主键约定**：`prompt_id = "prompt_" + apiId`（列表直查、更新直改、删除直删）。
  - `ai_config` 维护 client 与 prompt 的绑定（config_type='prompt'）。
  - `clientName` 与 `modelName` 解耦：Client 展示名用于房间/前端；Model 名称用于模型层。


## 5. 提示词工程（System Prompt & Context）

### 5.1 提示词合成机制 (Join -> Reconstruct -> Overwrite)

系统不再实时组装提示词变量，而是采用**入场即重构**的“快照”模式：

- **触发时机**：`Client/Agent` 成员被邀请进入房间时。
- **重构逻辑**：
  1. **原料获取**：从数据库读取 Bot 的 `originalPrompt`（原始人设）和当前房间的 `roomName/roomDesc`。
  2. **模板合成**：加载内置资源 `group-chat-template.txt`，将变量注入模板。
  3. **持久化覆盖**：将合成后的完整提示词写回 `ai_prompt.systen_prompt`。
  4. **缓存与内存刷新**：清理 Redis 缓存，强制触发 `Armory` 覆盖注册 Bean。
- **自愈能力**：合成逻辑会自动检测并提取已有的“原始人设”部分，防止在多次进出房间时产生提示词嵌套膨胀。

### 5.2 系统提示词模板（group-chat-template.txt）

- **存放位置**：`backend/ai-agent-app/src/main/resources/prompt/system-prompt/group-chat-template.txt`
- **注入变量**：
  - `{originalPrompt}`：Bot 的原始身份定义。
  - `{roomName}`：当前房间名称。
  - `{roomDesc}`：房间背景背景设定。
- **行为约束**：强制要求 Bot 保持人设、简洁回复、严禁带名称前缀（如 `[小A]:`）。

### 5.3 上下文装配（Context，普通消息）

- **数据来源**：最近 20 条群聊记录，经 `ContextAssembler` 转换为 `senderName: content` 的普通消息列表。
- **原则**：上下文仅作为 `UserMessage` 追加，**不干扰**已固化在 `SystemMessage` 中的身份定义。


## 6. 已实现能力（Delivered）

1) **策略化成员入场逻辑**  
   - 引入 `AbstractRoomMemberService` 模板类，统一 `basicJoin` 通用流程。
   - 实现 `User/Client/Agent` 不同策略子类，解耦入场加工细节。

2) **动态提示词重构引擎**  
   - 实现“入场即定性”的提示词装配流，支持从资源文件动态加载模板。
   - 解决 Bot 跨房间的人设隔离与场景感知问题。

3) **Bean 覆盖装配机制**  
   - 利用 `Armory` 的动态注册能力，实现内存中 `ChatClient` 实例的热更新，无需重启服务。

4) **WebSocket 实时群聊与 @ 指定回答**  
   - 完善上行 JSON 协议，后端 `RoomDispatchService` 优先强制命中被 @ 成员。

5) **领域模型层 (Repository) 增强**  
   - `AiRepository` 支持轻量级 `queryPromptByClientId` 与持久化 `updateSystenByPromptId`。


## 7. 规划中能力（Planned）

1) **调度决策解耦与规则链重构 (Priority: High)**  
   - 将当前 `RoomDispatchService` 中的硬编码逻辑（如 @命中、50% 概率）拆分为独立规则。
   - 引入 `DispatchRuleChain`，支持按优先级（如：`AtRule` > `ArbitratorRule` > `ProbabilityRule`）顺序执行。
   - 为后续“轮次推进/令牌抢占”提供插件化扩展点。

2) **仲裁者 (Arbitrator) 角色引入 (Priority: High)**  
   - 引入特殊 Bot 身份“仲裁者”，具备最高发言权控制逻辑。
   - 仲裁者可基于对话上下文或特定指令（如：`/stop`, `/next`）干预群聊流向。
   - 实现“发言令牌”机制，由仲裁者分配当前谁可以说话。

3) **基础设施缓存分级设计 (Infrastructure Cache)**  
   - 基于 `AbstractRepository` 实现带过期时间 (TTL) 的缓存。
   - **分级策略**：
     - 房间/成员信息：10-30 mins。
     - AI 静态配置：1 hour。
     - 模型/API 定义：12-24 hours。

4) **Agent 执行链接入（只渲染最终总结）**  
   - 激活房间内的 `AGENT` 成员，对接 `domain/ai/service/execute` 执行框架。

5) **多 Bot 智能答辩与游戏化模板**  
   - 结合 `ai_chat_room_state` 维护回合制规则（如辩论赛、狼人杀场景）。
   - 实现私有信息注入（私牌/秘密指令）与状态机流转。


## 8. 消息限流与截断策略（Guidelines）

- **后端建议**（领域配置/常量化）：
  - 单条回复最大字符数（如 2,000~3,000），超过截断并在结尾标注“…(truncated)”。
  - 历史上下文条数上限（如最近 20 条）；可根据房间策略调整。
  - 逐步引入分片流式（分段推送）与分页查询（游标/时间戳）。

- **前端建议**：
  - 聊天区内容容器 `overflow-y: auto`，避免撑高页面。
  - 超长消息折叠显示 + “展开更多”交互。


## 9. 关键目录与代码导航（By Task）

- **邀请成员（USER/CLIENT/AGENT）**  
  前端 `RoomChat.vue` → 后端 `RoomAdminService.joinRoom` → mapper `ai-chat-room-member-mapper.xml`

- **概率回复（CLIENT）**  
  `RoomDispatchService.dispatchNextSpeaker`（候选/概率/装配检查） → `RoomChatService.clientChat`（执行/落库/广播）

- **Client 人设与列表回显**  
  前端 `Setting.vue` → 控制器 `UserController` → 仓储 `UserRepository` → Prompt Mapper `AiPromptDao.xml`


## 10. 开发规范（Conventions）

- **事件名必须使用具体常量**：`USER_MSG/CLIENT_MSG/CLIENT_MSG_END`（保留 AGENT 系列）。
- **Prompt 主键统一**：`prompt_{apiId}`。历史数据若无该记录，列表/更新会“无效”，需补数据。
- **System Prompt 唯一**：仅在装配 ChatClient 时注入 `defaultSystem`；上下文仅作为普通消息。
- **成员类型严格区分**：USER/CLIENT/AGENT；memberId 显式对应用户名/clientId/agentId。前端统一用 `member_name` 展示。
- **仓储映射禁止 BeanUtils**：Infrastructure → Domain 手动 builder/set，保证可追踪与安全。
- **@ 指定回答上行协议**：前端直接传 `atMemberIds`（逗号分隔的 `memberId`），后端原样写入消息 `atMemberId` 字段并用于调度。

### WebSocket 上行协议（示例）

```json
{
  "content": "@小A 帮我准备周报",
  "traceId": "trace_1710000000000",
  "atMemberIds": "client_001,client_002"
}
```

说明：渲染用 `memberName`，协议传递用唯一 `memberId`；后端将 `atMemberIds` 写入消息 `atMemberId` 字段，调度强制命中被 @ 成员，其它成员走概率。


## 11. 路线图（Roadmap）

- M1：@ 指定回答（已完成）+ Agent 执行链接入（只渲染最终总结，进行中）。
- M2：仲裁官/裁判角色接入，支持发言权/终止控制；对话限流规则体系化。
- M3：游戏化模板支持（状态机/私有信息/回合推进），沉淀可复用场景范式。


---
> 本文为多 Bot 群聊系统的首版实现说明与架构指南。请严格遵循“系统提示词唯一 + 上下文普通化 + 事件名具体化 + 成员类型明确化”的原则推进演进。内置模板位于 `backend/ai-agent-app/src/main/resources/prompt`，自定义人设存于 `ai_prompt` 表（`prompt_{apiId}`）。未来接入 execute 时，仅渲染最终总结，保留思考过程。
