package com.dasi.infrastructure.adapter.port.xhspublish;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.adapter.port.IXhsMcpPort;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class XhsMcpGatewayAdapter implements IXhsMcpPort {

    private static final String TOOL_SUBMIT_IMAGE = "submit_publish_content_async";
    private static final String TOOL_QUERY_JOB = "get_publish_job_status";
    private static final String TOOL_VERIFY_NOTE = "verify_published_note";

    @Resource
    @Qualifier("xhsPublishMcpClient")
    private McpSyncClient mcpSyncClient;

    @Override
    public String submitImagePublish(String publishRequestJson) {
        return invoke(TOOL_SUBMIT_IMAGE, JSON.parseObject(publishRequestJson));
    }

    @Override
    public String queryJobStatus(String jobId) {
        return invoke(TOOL_QUERY_JOB, Map.of("job_id", jobId));
    }

    @Override
    public String verifyPublishedNote(String verifyRequestJson) {
        return invoke(TOOL_VERIFY_NOTE, JSON.parseObject(verifyRequestJson));
    }

    private String invoke(String toolName, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = mcpSyncClient.callTool(new McpSchema.CallToolRequest(toolName, arguments));
        String textContent = extractTextContent(result);
        if (textContent != null && !textContent.isBlank()) {
            return textContent;
        }

        JSONObject fallback = new JSONObject();
        fallback.put("tool", toolName);
        fallback.put("isError", result.isError());
        fallback.put("content", result.content());
        return JSON.toJSONString(fallback);
    }

    private String extractTextContent(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null || result.content().isEmpty()) {
            return null;
        }
        Object first = result.content().get(0);
        if (first == null) {
            return null;
        }
        try {
            Object text = first.getClass().getMethod("text").invoke(first);
            return text == null ? null : String.valueOf(text);
        } catch (Exception ignore) {
        }
        try {
            Object text = first.getClass().getMethod("getText").invoke(first);
            return text == null ? null : String.valueOf(text);
        } catch (Exception ignore) {
        }
        return String.valueOf(first);
    }

}

