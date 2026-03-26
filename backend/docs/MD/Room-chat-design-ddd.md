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
  - 调度与回复（概率触发、装配检查、指令下达）  
    `backend/ai-agent-domain/src/main/java/com/dasi/domain/room/service/chat/RoomDispatchService.java`
  - 对话执行（clientChat，同步调用 LLM，落库并广播）  
    `backend/ai-agent-domain/src/main/java/com/dasi/domain/room/service/chat/RoomChatService.java`
  - Armory 装配（Client/Agent Bean 装配）  
    `backend/ai-agent-domain/src/main/java/com/dasi/domain/ai/service/armory/strategy/ArmoryChatStrategy.java`  
    `backend/ai-agent-domain/src/main/java/com/dasi/domain/ai/service/armory/strategy/ArmoryWorkStrategy.java`（保留，未来接入）
  - 执行框架入口（未来对接 Agent 执行链，仅渲染最终总结）  
    `backend/ai-agent-domain/src/main/java/com/dasi/domain/ai/service/execute`

- Infrastructure（基础设施）
  - 房间仓储/成员映射/消息映射  
    `backend/ai-agent-infrastructure/src/main/java/com/dasi/infrastructure/repository/ChatRoomRepository.java`  
    `backend/ai-agent-infrastructure/src/main/resources/mapper/ai-chat-room-member-mapper.xml`  
    `backend/ai-agent-infrastructure/src/main/resources/mapper/ai-chat-room-message-mapper.xml`
  - 用户配置仓储（模型配置、Client、Prompt）  
    `backend/ai-agent-infrastructure/src/main/java/com/dasi/infrastructure/repository/UserRepository.java`  
    `backend/ai-agent-infrastructure/src/main/resources/mapper/AiPromptDao.xml`

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

### 5.1 系统提示词模板（defaultSystem，唯一 SystemMessage）

- **数据来源**：Client 的 `systemPrompt`（人设），创建/更新于模型配置页。
- **注入变量**（模板占位符，推荐 `{var}` 格式）：
  - `{clientName}`：当前 Client 的名称（如“女朋友”、“工具助手”）。
  - `{roomName}`：当前所处房间名。
  - `{roomId}`：当前房间唯一标识。
  - `{tone}`（可选）：情绪/语气风格。
  - `{botRole}`（可选）：扮演的角色名（如“仲裁官”、“辩手A”）。
- **存放位置**：项目内置模板放于  
  `backend/ai-agent-app/src/main/resources/prompt/system-prompt/*.txt`  
  自定义模板通过 `ai_prompt` 表维护（`prompt_{apiId}`）。
- **原则**：**只有一条**系统提示词（作为 defaultSystem 注入 ChatClient）；标识 Bot 的名字、房间信息、人设/语气等 **必须**在系统层明确。

### 5.2 上下文装配（Context，普通消息）

- **数据来源**：最近 N 条群聊记录，经 `ContextAssembler` 转换为 `senderName: content` 的普通消息列表（保持时间顺序）。
- **原则**：上下文只作为普通 User/Assistant Message 入 Prompt，**不再追加二次 SystemMessage**，避免覆盖人设或系统层含义。


## 6. 已实现能力（Delivered）

1) **WebSocket 实时群聊**  
   - 握手路径：`/miniagent/api/v1/ws/room/{roomId}/{username}`（浏览器原生 WS，不携带 Authorization）  
   - 对外事件：`USER_MSG`、`CLIENT_MSG`；内部信号：`CLIENT_MSG_END`  
   - 统一信封：`WebSocketEvent`（包含 eventType/payload/roomId/timestamp）

2) **Bot 候选与概率回复（CLIENT）**  
   - 候选仅取房间内 `member_type='CLIENT'` 的成员。  
   - 命中概率 → 检查 `client_{clientId}` Bean：不存在则触发 `ARMORY_CHAT` 装配，再调用 `clientChat`。  
   - 落库 `senderType='CLIENT'`，广播 `CLIENT_MSG`，内部触发 `CLIENT_MSG_END`。

3) **成员管理（加入/离开/名称冗余）**  
   - USER/CLIENT/AGENT 均可加入；Client/Agent 自动生成 `agent_session_id`。  
   - 已实现离开（踢出）接口：`RoomAdminService.leaveRoom(roomId, memberId)` → repository → mapper。

4) **模型配置（clientName + systemPrompt）**  
   - 新增/更新：携带 `clientName` 与 `systemPrompt`；`prompt_id=prompt_{apiId}`。  
   - 列表：`/user/model/list` 返回 `clientName + systemPrompt`（按 apiId 直查 prompt，避免复杂 join）。  
   - 删除：同步清理 `ai_config` 与对应 `ai_prompt`。

5) **前端体验**  
   - 左侧 Sidebar 固定不滚动；聊天区域独立滚动（避免整页高度膨胀）。  
   - 邀请弹窗支持邀请 **CLIENT** 与 **AGENT**；气泡渲染区分 USER/CLIENT/AGENT。


## 7. 规划中能力（Planned）

1) **专业提示词自动组装**  
   - 以模板 + 占位符方式管理（可视化编辑），保持人设与房间信息注入的标准化。  
   - 变量注入规范：`{clientName}` / `{roomName}` / `{roomId}` / `{tone}` / `{botRole}`…

2) **@ 指定回答**  
   - 用户消息识别 `@clientName` / `@agentName`，强制该 Bot 进入候选并回复（绕过随机概率）。

3) **Agent 执行链接入（只渲染最终总结）**  
   - 使用 `domain/ai/service/execute` 既有框架；保留思考/步骤在后端记录，不广播。  
   - 对外事件仍用 `CLIENT_MSG` 或新增 `AGENT_MSG`（待定）。

4) **多 Bot 智能答辩与仲裁官**  
   - “仲裁官（裁判）”作为特殊 Bot（Agent 或 Client），拥有“发言权控制/停止口令”等策略。  
   - 与“消息限流/轮次推进/令牌抢占”组合使用。

5) **游戏化扩展**  
   - 状态机 + 私有信息注入（如牌局私牌），结合 `ai_chat_room_state` 维护回合与规则。


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


## 11. 路线图（Roadmap）

- M1：完成 Agent 执行链接入（只渲染最终总结）+ @ 指定回答。
- M2：仲裁官/裁判角色接入，支持发言权/终止控制；对话限流规则体系化。
- M3：游戏化模板支持（状态机/私有信息/回合推进），沉淀可复用场景范式。


---

> 本文为多 Bot 群聊系统的首版实现说明与架构指南。请严格遵循“系统提示词唯一 + 上下文普通化 + 事件名具体化 + 成员类型明确化”的原则推进演进。内置模板位于 `backend/ai-agent-app/src/main/resources/prompt`，自定义人设存于 `ai_prompt` 表（`prompt_{apiId}`）。未来接入 execute 时，仅渲染最终总结，保留思考过程。*** End Patch*** End Patch  JSON``` ***
