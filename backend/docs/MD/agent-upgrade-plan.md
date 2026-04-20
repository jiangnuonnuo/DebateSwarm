## 企业决策辩论会（协作智能升级）研发方案

### Summary
基于现有“房间 + 辩论 + 仲裁 + trace 护栏”实现，下一阶段把系统升级成“可审计的多智能体决策引擎”，优先做协作智能而不是先做运营化功能。  
核心目标：从“选谁发言”升级为“多智能体协同产出可执行决策”。

### Key Changes
1. 协作协议升级（P0，先做）
- 新增“协作辩论协议”输出标准：每次发言必须结构化为 `claim/evidence/risk/action`，不再是纯文本闲聊。
- 在辩论配置中新增 `mode`（决策会模板）、`roleCard`（分析官/质疑官/验证官/总结官）、`qualityRubric`（评分维度）。
- 仲裁从“单次选人”升级为“双层决策”：`speaker arbitration` + `quality gate`（不达标自动补充追问或换人）。
- 变更重点落点：[`DebateService.java`](/F:/java/code/Agent/backend/ai-agent-domain/src/main/java/com/dasi/domain/room/service/debate/DebateService.java)、[`ContextAssemblerService.java`](/F:/java/code/Agent/backend/ai-agent-domain/src/main/java/com/dasi/domain/room/service/context/ContextAssemblerService.java)。

2. 智能体协作类型落地（P0-P1）
- 先落地“企业决策辩论会”模板：`Analyst vs Challenger + Verifier + Summarizer`。
- 把房间中的 `AGENT` 从“可入房但不发言”升级为“可参与决策角色”，复用现有 loop/step/react 执行链能力。
- 增加“共识状态”：`NO_CONSENSUS / PARTIAL / CONSENSUS`，并支持冲突保留（少数意见不丢失）。

3. 企业可用输出（P1）
- 新增决策时间线与报告能力：`timeline`（轮次事件）、`report`（结论、证据、风险、行动项、责任人建议）。
- 新增 API（不破坏现有接口）：
- `POST /chat-room/debate/mode/save`
- `GET /chat-room/debate/mode/list`
- `GET /chat-room/debate/timeline`
- `GET /chat-room/debate/report`
- `POST /chat-room/debate/decision/recommend`（AI 推荐胜方/结论，默认人工确认）
- 前端新增“证据卡片 + 共识进度 + 决策报告面板”，落点：[`RoomChat.vue`](/F:/java/code/Agent/frontend/src/components/RoomChat.vue)。

4. 企业治理补齐（P1，同步推进最小闭环）
- WebSocket 增加 token 校验 + 房间成员校验（防伪造用户名入房）。
- 房间接口补权限校验（创建者/成员角色）与审计日志。
- 增加并发与超时配额策略（房间级/租户级），避免多房间并发外呼失控。

### Test Plan
1. 领域测试
- 辩论状态机：`RUNNING -> ROUND_END -> NEXT_ROUND` 与失败补位不吞 turn。
- 双层仲裁：非法 speaker、低质量输出、空响应、超时降级路径。

2. 集成测试
- 决策会模板全链路：开始辩论 -> 多角色协作 -> 生成 report。
- `AGENT + CLIENT` 混合参与下的调度一致性与 trace 护栏有效性。

3. 企业验收
- 安全：非成员无法收发房间消息，伪造用户名连接被拒绝。
- 可追溯：任一结论可回放到轮次、发言、证据来源。
- 稳定性：并发房间压测下无幽灵回调污染。

### Assumptions
- 你已确认优先级为“协作智能升级”，场景为“决策辩论会”。
- 默认采用“AI 推荐结论 + 人工确认”而非全自动判胜，降低企业决策风险。
- 现有 API 保持兼容，新增能力通过增量接口提供。
