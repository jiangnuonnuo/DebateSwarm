# 提示词污染人工回填指南（不自动批修）

## 1. 背景

- 历史版本存在“CLIENT 入房后把房间模板写回 `ai_prompt.systen_prompt`”的问题。
- 结果是同一条 prompt 被多次包裹，出现重复段落、语义漂移、长度异常。
- 当前版本已改为“运行时动态注入”，不再继续污染；本指南只处理历史脏数据。

## 2. 排查范围

重点检查：

- 房间中经常被邀请/重装配的 `CLIENT`
- 仲裁者 `clientId`
- 含 `{debateInstruction}` 但文本已多段重复的 prompt

建议 SQL（只用于排查）：

```sql
SELECT prompt_id, prompt_name, LENGTH(systen_prompt) AS prompt_len, update_time
FROM ai_prompt
ORDER BY prompt_len DESC, update_time DESC;
```

## 3. 判定规则

任意满足以下任一条即可判定“疑似污染”：

- 同一段标题/小节重复出现 2 次及以上（例如“核心人设/场景上下文/社交规范”反复拼接）
- 文本中包含多个重复的 `{debateInstruction}` 区块
- prompt 长度显著超过该角色正常人设长度

## 4. 人工回填步骤

1. 备份当前脏数据（按 `prompt_id` 导出）。
2. 准备目标“原始人设”文本（不包含房间动态快照）。
3. 按 `prompt_id` 回写 `systen_prompt`。
4. 清理相关缓存并触发一次装配预热。
5. 进入房间联调，确认内容不再增长、仲裁正常。

示例回写 SQL：

```sql
UPDATE ai_prompt
SET systen_prompt = :clean_system_prompt
WHERE prompt_id = :prompt_id;
```

## 5. 回填后校验

- 同一 `client` 多次入房后，`ai_prompt.systen_prompt` 长度保持稳定
- 运行日志出现 `PROMPT_PERSIST_BLOCKED`，且不再出现入房写 prompt 的日志
- 仲裁日志出现 `ARBITRATOR_RUNTIME_INSTRUCTION_RENDERED`，说明动态指令走运行时路径

## 6. 注意事项

- 本次策略为“人工确认后逐条修复”，不执行自动批量清洗脚本。
- 如某些 prompt 已包含业务定制段，请优先保留人工定制内容，只移除重复包裹段。
