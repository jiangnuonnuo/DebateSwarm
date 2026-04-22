# 真实会议模式 V1 后端交付说明

## 1. 接口清单与示例

### 1.1 创建会议
- `POST /miniagent/api/v1/chat-room/meeting/create`
```json
{
  "roomId": "room_xxx",
  "topic": "需求评审会",
  "goal": "确定V1范围与行动项",
  "creatorId": "boss_001",
  "creatorRole": "boss",
  "pmId": "pm_001",
  "roleAssignments": {
    "boss": "boss_001",
    "pm": "pm_001",
    "product": "product_001",
    "architect": "architect_001",
    "backend": "backend_001",
    "frontend": "frontend_001",
    "tester": "tester_001"
  }
}
```

### 1.2 开始会议
- `POST /miniagent/api/v1/chat-room/meeting/start`
```json
{
  "roomId": "room_xxx",
  "meetingId": "meeting_xxx",
  "actorId": "pm_001",
  "actorRole": "pm",
  "expectedVersion": 0
}
```

### 1.3 执行动作
- `POST /miniagent/api/v1/chat-room/meeting/action`
```json
{
  "roomId": "room_xxx",
  "meetingId": "meeting_xxx",
  "actionType": "RETURN",
  "actorId": "architect_001",
  "actorRole": "architect",
  "targetId": "product_001",
  "targetRole": "product",
  "reason": "验收口径不完整",
  "requiredFix": "补齐边界条件与验收标准",
  "deadline": "2026-04-22T18:00:00",
  "clientRequestId": "req-20260421-001",
  "traceId": "trace-20260421-001",
  "expectedVersion": 1
}
```

### 1.4 查询状态
- `GET /miniagent/api/v1/chat-room/meeting/status?roomId=room_xxx&meetingId=meeting_xxx`

### 1.5 查询时间线
- `GET /miniagent/api/v1/chat-room/meeting/timeline?roomId=room_xxx&meetingId=meeting_xxx`

## 2. 关键 SQL（仅必要字段）

### 2.1 查询会议主状态
```sql
SELECT id, meeting_id, room_id, topic, goal, status, round_number,
       owner_id, owner_role, pm_id, role_assignments_json, version, create_time, update_time
FROM ai_meeting_session
WHERE meeting_id = ?
  AND is_deleted = 0
LIMIT 1;
```

### 2.2 查询房间活动会议
```sql
SELECT id, meeting_id, room_id, topic, goal, status, round_number,
       owner_id, owner_role, pm_id, role_assignments_json, version, create_time, update_time
FROM ai_meeting_session
WHERE room_id = ?
  AND status IN ('PENDING', 'IN_PROGRESS', 'RETURNED', 'WAIT_DECISION', 'NEXT_ROUND_PENDING')
  AND is_deleted = 0
ORDER BY update_time DESC, id DESC
LIMIT 1;
```

### 2.3 查询动作时间线
```sql
SELECT id, action_id, meeting_id, room_id, action_type, actor_id, actor_role,
       target_id, target_role, payload, reason, required_fix, deadline, result,
       meeting_version, client_request_id, trace_id, create_time
FROM ai_meeting_action
WHERE meeting_id = ?
  AND is_deleted = 0
ORDER BY create_time ASC, id ASC
LIMIT ?;
```

### 2.4 幂等查询
```sql
SELECT id, action_id, meeting_id, room_id, action_type, actor_id, actor_role,
       target_id, target_role, payload, reason, required_fix, deadline, result,
       meeting_version, client_request_id, trace_id, create_time
FROM ai_meeting_action
WHERE meeting_id = ?
  AND client_request_id = ?
  AND is_deleted = 0
LIMIT 1;
```

### 2.5 解决开放问题单
```sql
UPDATE ai_meeting_issue
SET status = 'RESOLVED',
    resolved_at = ?,
    update_time = CURRENT_TIMESTAMP
WHERE meeting_id = ?
  AND assignee_role = ?
  AND status = 'OPEN'
  AND is_deleted = 0;
```

## 3. 索引命中验证建议

### 3.1 活动会议查询
```sql
EXPLAIN
SELECT id, meeting_id, room_id, topic, goal, status, round_number,
       owner_id, owner_role, pm_id, role_assignments_json, version, create_time, update_time
FROM ai_meeting_session
WHERE room_id = 'room_xxx'
  AND status IN ('PENDING', 'IN_PROGRESS', 'RETURNED', 'WAIT_DECISION', 'NEXT_ROUND_PENDING')
  AND is_deleted = 0
ORDER BY update_time DESC, id DESC
LIMIT 1;
```
- 预期命中索引：`idx_room_active`

### 3.2 时间线查询
```sql
EXPLAIN
SELECT id, action_id, meeting_id, room_id, action_type, actor_id, actor_role,
       target_id, target_role, payload, reason, required_fix, deadline, result,
       meeting_version, client_request_id, trace_id, create_time
FROM ai_meeting_action
WHERE meeting_id = 'meeting_xxx'
  AND is_deleted = 0
ORDER BY create_time ASC, id ASC
LIMIT 200;
```
- 预期命中索引：`idx_meeting_timeline`

### 3.3 幂等查询
```sql
EXPLAIN
SELECT id, action_id, meeting_id, room_id, action_type, actor_id, actor_role,
       target_id, target_role, payload, reason, required_fix, deadline, result,
       meeting_version, client_request_id, trace_id, create_time
FROM ai_meeting_action
WHERE meeting_id = 'meeting_xxx'
  AND client_request_id = 'req-20260421-001'
  AND is_deleted = 0
LIMIT 1;
```
- 预期命中索引：`uk_meeting_request`

## 4. 技术债登记（按架构师分级）

- D1：登录态身份与角色强校验（`UserContext` 对齐）  
  - 当前状态：延期到 V1.5 治理补强阶段  
  - 原因：本轮按要求只修流程闭环阻塞项（M1/M2/M3）

- D2：`client_request_id` 非空约束（DDL + 服务端必填校验）  
  - 当前状态：延期到 V1.5 稳定性增强阶段  
  - 原因：本轮保留兼容性，不阻塞当前联调
