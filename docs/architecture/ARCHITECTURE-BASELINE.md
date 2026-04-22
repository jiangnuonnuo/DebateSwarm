# Agent Architecture Baseline

## 1. Scope
- Project root reviewed: `backend/`, `frontend/`, `mcp/`, `README.md`, and existing `backend/docs/MD/agent-upgrade-plan.md`.
- File-level scan performed with `rg --files` across all modules.
- Baseline date: `2026-04-21`.

## 2. Current Architecture Style (as implemented)

### 2.1 Overall style
- Modular monolith for core business (`backend/ai-agent-*` multi-module Maven).
- Layered + DDD-inspired split:
  - `ai-agent-trigger`: inbound adapters (REST, WebSocket listeners, MQ listeners)
  - `ai-agent-api`: API contracts and DTO contracts
  - `ai-agent-domain`: domain services, strategy engines, domain models, domain ports
  - `ai-agent-infrastructure`: persistence, cache, MQ, websocket push, repository implementations
  - `ai-agent-app`: composition root and runtime configuration
  - `ai-agent-types`: shared constants, exceptions, result wrappers, annotations

### 2.2 Execution engine design
- Work-agent execution is strategy-driven:
  - `loop`, `step`, `react` strategies selected by agent type.
  - Factory + strategy map: `ExecuteStrategyFactory`.
  - Node-router pipeline via wrench strategy tree (`AbstractExecuteNode` + root nodes).
- Runtime output channel:
  - SSE is the primary async response stream for work execution.
  - Message persistence occurs during pipeline emission.

### 2.3 Room and debate runtime
- Event-driven room orchestration:
  - WebSocket input -> domain dispatch service.
  - External broadcast + internal Spring events (`RoomEventPublisher`).
  - Internal async listener (`RoomAgentListener`) triggers next-speaker decision.
- Debate orchestration is state-machine-like with guard checks:
  - Session state persistence in repository.
  - Trace/version/session guard to drop stale callbacks.
  - Round lifecycle control with transactional boundaries.

### 2.4 Workspace async command flow
- User-side workspace actions publish MQ events.
- Main/retry/dead queues handled by RabbitMQ config + listeners.
- Idempotency marker stored in Redis for processed event protection.

### 2.5 Persistence model
- Polyglot persistence:
  - MySQL via MyBatis mapper XML for transactional business data.
  - PostgreSQL + PgVector for RAG vectors.
  - Redis for cache, idempotency flags, and runtime state.
- Repository pattern is used for domain access abstraction.

### 2.6 Frontend style
- SPA (Vue3 + Vite + Pinia + vue-router + Axios).
- Route-centric composition with component pages under `frontend/src/components`.
- API gateway module at `frontend/src/request/api.js` centralizes endpoint contracts.

### 2.7 MCP subsystem
- Independent MCP server modules under `mcp/` (amap, bocha, email, wecom, csdn).
- Each MCP server exposes tool callbacks through Spring AI `@Tool`.
- Main backend calls MCP through Spring AI client composition.

## 3. Architecture Classification
- The codebase is best classified as:
  - `DDD-inspired layered modular monolith` + `strategy-tree execution engine` + `event-driven room orchestration` + `async MQ command processing`.
- It is not a full microservice backend for core business; MCP tools are split as separate services.

## 4. Code Placement Rules (authoritative for future tasks)

## 4.1 Backend placement
- New external HTTP endpoint:
  - Contract in `backend/ai-agent-api/src/main/java/com/dasi/api/**`
  - Controller adapter in `backend/ai-agent-trigger/src/main/java/com/dasi/trigger/controller/**`
- New domain capability:
  - Domain service + model in `backend/ai-agent-domain/src/main/java/com/dasi/domain/<bounded-context>/**`
- New persistence/query implementation:
  - Domain repository interface in `domain/**/repository`
  - Implementation in `backend/ai-agent-infrastructure/src/main/java/com/dasi/infrastructure/repository/**`
  - MyBatis DAO + XML in `backend/ai-agent-infrastructure/src/main/java/com/dasi/infrastructure/persistent/dao/**` and `resources/mapper/**`
- New integration adapter (redis/mq/websocket/oss/etc):
  - Domain port in `domain/**/apapter/port` or `domain/util/**`
  - Adapter implementation in `infrastructure/**`
- New app/runtime config:
  - `backend/ai-agent-app/src/main/java/com/dasi/config/**`

## 4.2 Frontend placement
- New page route:
  - Component in `frontend/src/components/`
  - Route entry in `frontend/src/router/router.js`
- New API:
  - Add request function in `frontend/src/request/api.js`
  - Keep transport concerns in `frontend/src/request/request.js`
- Shared state:
  - Add store slice/action in `frontend/src/router/pinia.js`

## 4.3 MCP placement
- New MCP tool server:
  - New module under `mcp/mcp-server-<name>/`
  - `Application` class wires Retrofit/port/tool provider
  - `mcp/tool/*Tool.java` exposes `@Tool` methods

## 5. Design Constraints To Keep
- Keep controller layer thin; business orchestration stays in domain services.
- Keep domain interfaces independent from infrastructure implementation classes.
- Keep event contracts explicit for room/debate events; preserve trace guard semantics.
- Preserve async boundaries:
  - SSE for work streaming
  - MQ for workspace command eventual consistency
  - Internal application events for room dispatch bridge

## 6. Immediate Risks and Attention Points
- Mapper quality risk:
  - SQL in `AiAgentDao.xml` appears to include an invalid statement in `queryAgentNameByAgentId`; verify and fix before release hardening.
- Naming consistency risk:
  - `apapter` package typo exists and should be normalized in a planned refactor, not mixed into feature tasks.
- Domain size risk:
  - `DebateService` is very large; consider progressive extraction by responsibility (state, arbitration, guard, notice, persistence coordination).

## 7. Architecture Planning Workflow For Future Tasks
- Step 1: Define bounded context and ownership (ai/session/workspace/room/admin/user).
- Step 2: Choose integration style (sync REST, SSE stream, MQ async, internal event).
- Step 3: Place files strictly by rule in section 4.
- Step 4: Add traceability note in architect role document under `Plan/roles/`.
- Step 5: Validate no cross-layer leakage before merge.

