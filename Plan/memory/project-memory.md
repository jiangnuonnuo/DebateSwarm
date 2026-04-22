# Project Memory

## Durable Decisions
-

## Open Risks
-

## Stable Constraints
- backend role hard constraints (added 2026-04-21):
  - 关键步骤与字段必须注释，禁止尾注释。
  - 日志必须可定位：roomId/meetingId/actionType/actorId/actorRole/fromStatus/toStatus/version/traceId。
  - SQL 仅查必要字段，必须命中索引，禁止 select *。
  - 对象转换手写映射，方法按职责拆分，保持高解耦。
- Meeting V1 persistence baseline:
  - 动作请求必须携带 expectedVersion，建议始终携带 clientRequestId 做幂等。
  - 会议状态以服务端为单一真相，前端不做本地状态推断。
  - 会议持久化依赖 `backend/docs/docker/conf/meeting-table.sql` 先执行建表。

## Manager Cycle 2026-04-21 18:26 - work-product-planning-001
- Source verification: Plan/verification/verification-report-work-product-planning-001-20260421-1826.md
- Open evidence gaps: 0

## Manager Cycle 2026-04-21 20:26 - work-real-meeting-mode-002
- Source verification: Plan/verification/verification-report-work-real-meeting-mode-002-20260421-2026.md
- Open evidence gaps: 0

## Manager Cycle 2026-04-21 20:46 - work-meeting-v1-delivery-003
- Source verification: Plan/verification/verification-report-work-meeting-v1-delivery-003-20260421-2046.md
- Open evidence gaps: 3

## Manager Cycle 2026-04-21 22:44 - work-meeting-v1-delivery-003
- Source verification: Plan/verification/verification-report-work-meeting-v1-delivery-003-20260421-2244.md
- Open evidence gaps: 2

## Manager Cycle 2026-04-22 10:16 - work-meeting-v1-delivery-003
- Source verification: Plan/verification/verification-report-work-meeting-v1-delivery-003-20260422-1016.md
- Open evidence gaps: 2

## Manager Cycle 2026-04-22 10:27 - work-meeting-v1-delivery-003
- Source verification: Plan/verification/verification-report-work-meeting-v1-delivery-003-20260422-1027.md
- Open evidence gaps: 2

## Manager Cycle 2026-04-22 10:47 - work-meeting-v1-delivery-003
- Source verification: Plan/verification/verification-report-work-meeting-v1-delivery-003-20260422-1047.md
- Open evidence gaps: 2

## Manager Cycle 2026-04-22 10:51 - work-meeting-v1-delivery-003
- Source verification: Plan/verification/verification-report-work-meeting-v1-delivery-003-20260422-1051.md
- Open evidence gaps: 2
