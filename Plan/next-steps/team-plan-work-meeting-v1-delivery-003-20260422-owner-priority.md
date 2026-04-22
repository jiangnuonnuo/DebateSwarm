# Team Next-Step Plan - work-meeting-v1-delivery-003 (Owner Priority 2026-04-22)

## Priority Rule
1. 当前迭代只处理影响流程闭环的阻塞项（M1/M2/M3）。
2. 权限治理与幂等DDL强化列入后续阶段（D1/D2）。

## Blocking Fix Tasks (Current Iteration)
| Task ID | Owner | Task | Done Definition |
|---|---|---|---|
| M1 | backend-1 | 补齐 `NEXT_ROUND_PENDING -> IN_PROGRESS` 可恢复路径，并保证可开启下一轮 | 状态机可从下一轮待开恢复到进行中，且不阻断新流程 |
| M2 | backend-1 | 对 `RETURN/RESUME` 强制目标角色校验，避免问题单失配与误关 | 退回问题单必须带目标角色，恢复仅关闭目标角色问题单 |
| M3 | backend-1 | 落地 `roundNumber` 推进规则（开始=1，进入下一轮递增） | 状态查询返回轮次与实际流程一致 |

## Deferred Tech Debt (Next Phase)
| Debt ID | Owner | Topic | Planned Stage |
|---|---|---|---|
| D1 | backend-1 | 登录态身份与角色强校验（UserContext 对齐） | V1.5 治理补强 |
| D2 | backend-1 | `client_request_id` 非空约束与服务端必填校验 | V1.5 稳定性补强 |

## Coordination
| Agent | Task |
|---|---|
| frontend-1 | 按 M1/M2/M3 新接口语义联调，并回填界面状态机展示证据 |
| tester-1 | 先覆盖 M1/M2/M3 验收用例；D1/D2 仅登记风险不拦截本轮 |
| architect-1 | 跟踪 M1/M2/M3 落地一致性并在下一轮 manager cycle 做验证 |

