# 真实会议模式后端落地规划（backend-001）

## 1. 输入基线
- 产品：`docs/product/REAL-MEETING-MODE-PRD-V2.md`
- 架构：`docs/architecture/REAL-MEETING-MODE-ARCHITECTURE-V1.md`
- 执行框架：`docs/architecture/REAL-MEETING-MODE-V1-DELIVERY-FRAMEWORK.md`

## 2. 当前代码现实结论
- 已有可复用基础：
  - `room` 域事件发布链路（WebSocket + 内部事件）。
  - `debate` 域的状态机、乐观锁更新、可观测 notice 模式。
  - 统一 `trigger/api/domain/infrastructure` 分层规范。
- 现阶段缺口：
  - 缺少会议治理的独立领域对象与接口。
  - 缺少会议动作流水与退回问题单实体。
  - 缺少会议控制台后端专用 API。

## 3. 版本化落地路线

### V1（会议骨架，5 个工作日）
- 目标：跑通会议创建、开始、动作执行、状态查询、时间线回放。
- 后端范围：
  - 新增 `meeting-governance` 领域模型（MeetingSession/Action/Issue）。
  - 新增 `/chat-room/meeting/*` API：
    - `POST /create`
    - `POST /start`
    - `POST /action`
    - `GET /status`
    - `GET /timeline`
  - 新增最小状态机与权限矩阵（boss/pm/architect/product）。
  - 接入系统通知，保证前端可监听会议动作变化。
- DoD：
  - 会议状态可按规则迁移。
  - 动作流水可查询并可追溯执行人。
  - 退回动作可生成问题单并支持恢复。

### V2（持久化增强，4 个工作日）
- 目标：将会议数据从临时存储升级为 DB 持久化。
- 后端范围：
  - 增加 `ai_meeting_session / ai_meeting_action / ai_meeting_issue / ai_meeting_summary` DAO 与 Mapper。
  - 引入乐观锁版本控制（meeting session）。
  - 增加历史查询分页能力。
- DoD：
  - 服务重启后会议状态与时间线不丢失。
  - 冲突写入可被拒绝并提示刷新重试。

### V3（调度融合，5 个工作日）
- 目标：把会议治理与房间调度树融合，形成“会议优先”调度。
- 后端范围：
  - 在 dispatch 树接入 `MeetingGovernorNode`。
  - 会议进行中优先处理结构化动作，非会议消息回落现有策略。
  - 统一 `@` 指令结构化解析并透传动作上下文。
- DoD：
  - 会议动作对调度链路可见。
  - 并发场景不出现状态漂移。

### V4（治理审计，3 个工作日）
- 目标：提升组织治理与审计复盘能力。
- 后端范围：
  - 增加审计检索接口（按会议、角色、时间段）。
  - 增加阶段结论导出与下一轮条件校验。
  - 增加关键风险告警（越权动作、非法迁移）。
- DoD：
  - 可按 meetingId 完整复盘关键决策链。
  - 异常动作可追踪到执行角色与输入参数。

## 4. 时间排期（建议）
- Day 1: V1 领域模型 + API 协议 + Controller 骨架。
- Day 2: V1 状态机与权限规则 + 动作流水。
- Day 3: V1 退回问题单与恢复逻辑 + notice 联动。
- Day 4: 自测 + 联调支持（status/timeline/错误码）。
- Day 5: 缺陷修复 + 文档与验收准备。

## 5. 风险与对策
- 风险：角色绑定与真实成员不一致导致越权判断失真。  
  对策：V1 增加“角色绑定一致性校验”，V2 接入成员表强校验。
- 风险：高并发动作造成状态覆盖。  
  对策：V1 保留 expectedVersion 入参，V2 切换 DB 乐观锁。
- 风险：前后端状态字段不统一。  
  对策：统一 `MeetingStatus` 代码值，并在 API DTO 明确输出。

## 6. 本轮实现范围声明
- 已完成：V1 的后端最小闭环代码（API + 领域服务 + 仓储 + 控制器 + 状态机）。
- 待下一轮：V2 持久化落库与调度树深度接入。
