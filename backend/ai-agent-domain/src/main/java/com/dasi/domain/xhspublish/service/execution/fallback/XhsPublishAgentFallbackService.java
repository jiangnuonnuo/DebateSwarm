package com.dasi.domain.xhspublish.service.execution.fallback;

import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;
import com.dasi.domain.xhspublish.service.support.XhsPublishClientSupport;
import com.dasi.types.exception.WorkException;
import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class XhsPublishAgentFallbackService implements IXhsPublishAgentFallbackService {

    @Resource
    private XhsPublishClientSupport clientSupport;

    @Resource
    private XhsPublishAgentFallbackPromptBuilder promptBuilder;

    @Resource
    private XhsPublishAgentFallbackResultParser resultParser;

    @Resource
    @Qualifier("xhsPublishMcpClient")
    private McpSyncClient xhsPublishMcpClient;

    @Override
    public XhsPublishRemoteResult execute(XhsPublishExecutionContext executionContext) {
        try {
            // 步骤 F1：从 latestContextJson.ext 取发布 client，不重新选择模型、不重新生成文案。
            String clientId = resolveClientId(executionContext);

            // 步骤 F2：构造“只允许执行工具”的兜底 prompt，把 publishRequestJson 原样交给模型。
            Prompt prompt = promptBuilder.build(executionContext);
            String rawResponse = clientSupport.promptOnceWithTools(
                    clientId,
                    prompt,
                    new SyncMcpToolCallbackProvider(xhsPublishMcpClient),
                    "Agent 发布兜底结果为空",
                    "Agent 发布兜底失败，请稍后重试"
            );

            // 步骤 F3：把模型最终输出归一成和后端直调一致的 RemoteResult 结构。
            return resultParser.parse(rawResponse);
        } catch (Exception e) {
            // 步骤 F4：兜底自身失败时，也统一回写成 transient failed，不让执行树再分叉。
            String message = sanitizeMessage(e.getMessage());
            log.warn("【小红书发布】Agent 兜底发布失败：taskId={}, attemptId={}, message={}",
                    executionContext.getTask().getTaskId(),
                    executionContext.getAttempt().getAttemptId(),
                    message);
            JSONObject result = new JSONObject();
            result.put("status", "failed");
            result.put("executor", "agent_fallback");
            result.put("error_code", "AGENT_FALLBACK_FAILED");
            result.put("error_type", "transient");
            result.put("error_message", message);
            return XhsPublishRemoteResult.builder()
                    .status("failed")
                    .resultJson(result.toJSONString())
                    .errorCode("AGENT_FALLBACK_FAILED")
                    .errorType("transient")
                    .errorMessage(message)
                    .executor("agent_fallback")
                    .build();
        }
    }

    private String resolveClientId(XhsPublishExecutionContext executionContext) {
        // clientId 固定从 ext 中读取，保证一键提交阶段和执行兜底阶段使用同一个 client。
        JSONObject sourceContext = executionContext.getSourceContext();
        JSONObject ext = sourceContext == null ? null : sourceContext.getJSONObject("ext");
        String clientId = ext == null ? null : ext.getString("clientId");
        if (!StringUtils.hasText(clientId)) {
            throw new WorkException("缺少发布 clientId，无法执行 Agent 兜底");
        }
        return clientId.trim();
    }

    private String sanitizeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "Agent 发布兜底失败";
        }
        String compact = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (compact.length() <= 240) {
            return compact;
        }
        return compact.substring(0, 240) + "...";
    }

}
