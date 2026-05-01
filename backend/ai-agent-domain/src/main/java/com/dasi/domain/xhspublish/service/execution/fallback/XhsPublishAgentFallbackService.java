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
            String clientId = resolveClientId(executionContext);
            Prompt prompt = promptBuilder.build(executionContext);
            String rawResponse = clientSupport.promptOnceWithTools(
                    clientId,
                    prompt,
                    new SyncMcpToolCallbackProvider(xhsPublishMcpClient),
                    "Agent 发布兜底结果为空",
                    "Agent 发布兜底失败，请稍后重试"
            );
            return resultParser.parse(rawResponse);
        } catch (Exception e) {
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
