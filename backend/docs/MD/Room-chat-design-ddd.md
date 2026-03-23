# AI Agent 多智能体群聊系统：从 0 到 1 的演进之路 (DDD 架构)

## 1. 项目背景与愿景 (Project Background & Vision)
传统的 AI 对话系统大多局限于 **“1对1”** 的单兵作战模式，难以满足日益复杂的协同工作与娱乐竞技需求。本项目的核心目标是构建一个 **“多智能体群聊房间”**，让多个 AI Agent 在同一时空下进行交互。

### 核心意义
- **协同创新**：不同专长的 Agent（如架构师、开发、测试）在同一群聊中共同解决复杂问题。
- **博弈竞技**：实现如“斗牛”、“炸金花”、“技术辩论”等强规则、多回合的对抗场景。
- **动态交互**：用户作为房间的发起者或参与者，可以随时介入 AI 之间的对话。

---

## 2. 核心功能设计 (Core Features)

### 2.1 基础群聊功能
- **全员广播**：用户发送一条消息，房间内所有选中的 Agent 同时看到并产生响应。
- **身份锚定**：每个 Agent 在群聊中明确知道自己的名字，并能识别出其他成员的发言。
- **上下文装配**：自动提取最近 20 条消息，将其平铺为 `[姓名]: 内容` 格式，供 AI 理解对话流。

### 2.2 进阶交互逻辑
- **令牌抢占 (Grab Mode)**：模拟人类抢话，多个 Agent 根据权重或随机概率抢夺发言权，降低 Token 消耗。
- **场景仲裁 (Arbiter)**：引入专门的“仲裁 Agent”，根据当前对话氛围或游戏规则，指派特定的 Agent 发言。
- **状态同步 (State Sync)**：通过 `ai_chat_room_state` 表实时同步房间的公共状态（如牌局、进度、分数）。

---

## 3. 从 0 到 1 的雏形演进计划 (Roadmap)

本系统采用 **“小步快跑、迭代升级”** 的开发策略，确保每一步都可验证。

### 第一阶段：MVP 雏形（当前重点）
**目标：实现最基础的“全员广播”对话。**
- **通信机制**：从 SSE 升级为 **WebSocket**，支持单连接下多 Agent 的并发流式回传。
- **分发策略**：
    - **全员响应**：用户一句话，房间内所有 Agent 默认全部回复。
    - **概率响应**：通过简单的 `Random` 逻辑，决定每个 Agent 是否回复（例如 50% 概率）。
- **前端呈现**：支持根据 `sender_id` 动态渲染多个对话气泡。

### 第二阶段：规则化与抢占
**目标：引入发言控制权。**
- **Redis 信号量**：在 Agent 调用 LLM 前，先去 Redis 抢占令牌，抢到者方可发言。
- **@指定功能**：用户在消息中 @ 某个 Agent 时，强制该 Agent 获取发言令牌。
- **任务状态机**：记录每个 Agent 的任务状态（排队中、思考中、已完成）。

### 第三阶段：场景驱动（如“斗牛”游戏）
**目标：支持复杂业务逻辑。**
- **状态机落地**：利用 `ai_chat_room_state` 记录游戏回合、每个人的手牌。
- **私有数据隔离**：在装配上下文时，只给特定 Agent 注入其私有的牌面信息。
- **循环/顺序执行**：实现固定顺序（1->2->3）的发言逻辑。

---

## 4. 系统架构设计 (DDD Layers)

| 层次 | 核心职责 |
| :--- | :--- |
| **Trigger (触发层)** | WebSocket 管理、HTTP 请求接入、权限校验。 |
| **Application (应用层)** | `RoomChatService`：业务编排中心，负责协调上下文装配、令牌抢占与消息分发。 |
| **Domain (领域层)** | `ContextAssembler`：上下文装配器，将历史消息转化为 AI 的 System Prompt。<br>`IRoomRepository`：领域仓库接口。 |
| **Infrastructure (基础设施层)** | `ChatRoomRepository`：实现 PO 与 Entity 的**手动映射**（禁止 BeanUtils）。<br>`RoomEventPublisher`：基于 WebSocket 的实时推送。 |

---

## 5. 数据库地基 (Data Schema)

| 表名 | 索引设计关键 | 扩展策略 |
| :--- | :--- | :--- |
| `ai_chat_room` | `idx_room_id` | `ext_config` 存储场景规则（JSON 字符串）。 |
| `ai_chat_room_member` | `idx_room_agent` | `agent_session_id` 用于隔离私有上下文。 |
| `ai_chat_room_message` | `idx_room_time` | `ORDER BY create_time DESC` 提升分页效率。 |
| `ai_chat_room_state` | `idx_room_version` | 乐观锁 `version` 保证高并发状态更新。 |

---

## 6. 开发者协议与规范 (Developer Guidelines)

- **手动映射规范**：从 `Infrastructure` 到 `Domain` 的所有对象转换，必须手动 `builder` 或 `set`，严禁使用 `BeanUtils`，确保类型安全与字段追踪。
- **SQL 过滤**：所有 SQL 查询必须显式包含 `is_deleted = 0`，避免逻辑删除的数据泄露。
- **上下文反转**：从数据库取出的消息是 `DESC`（最新的在前），装配给 AI 时必须先 `reverse` 为 `ASC`（按时间顺序）。
- **WebSocket 统一信封**：所有下行数据必须包装在 `WebSocketEvent` 中。

---
*注：本文件为项目长期演进的唯一事实来源。后续开发需严格遵守路线图，确保地基打稳后再进行功能扩展。*
@Author: xerina
@Description: AI Agent Multi-Agent Chatroom Design (DDD)
