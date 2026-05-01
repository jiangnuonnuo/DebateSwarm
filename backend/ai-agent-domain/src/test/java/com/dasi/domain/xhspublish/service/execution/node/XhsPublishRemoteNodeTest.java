package com.dasi.domain.xhspublish.service.execution.node;

import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.fallback.IXhsPublishAgentFallbackService;
import com.dasi.domain.xhspublish.service.execution.remote.IXhsPublishRemoteExecutor;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XhsPublishRemoteNodeTest {

    private XhsPublishRemoteNode remoteNode;

    @Mock
    private IXhsPublishRemoteExecutor remoteExecutor;

    @Mock
    private IXhsPublishAgentFallbackService agentFallbackService;

    @BeforeEach
    void setUp() {
        remoteNode = new XhsPublishRemoteNode();
        ReflectionTestUtils.setField(remoteNode, "remoteExecutor", remoteExecutor);
        ReflectionTestUtils.setField(remoteNode, "agentFallbackService", agentFallbackService);
    }

    @Test
    void shouldUseAgentFallbackWhenTransientFailureHasNoJobId() {
        XhsPublishExecutionContext context = buildContext("client-xhs");
        XhsPublishRemoteResult primary = XhsPublishRemoteResult.builder()
                .status("failed")
                .errorCode("REMOTE_CALL_EXCEPTION")
                .errorType("transient")
                .errorMessage("unexpected status code: 405")
                .resultJson("{\"status\":\"failed\",\"executor\":\"backend_mcp\"}")
                .executor("backend_mcp")
                .build();
        XhsPublishRemoteResult fallback = XhsPublishRemoteResult.builder()
                .status("published")
                .resultJson("{\"status\":\"published\",\"executor\":\"agent_fallback\"}")
                .executor("agent_fallback")
                .build();

        when(remoteExecutor.executeImagePublish(anyString())).thenReturn(primary);
        when(agentFallbackService.execute(context)).thenReturn(fallback);

        XhsPublishRemoteResult result = remoteNode.executeRemoteWithFallback(context);

        assertSame(fallback, result);
        verify(agentFallbackService).execute(context);
    }

    @Test
    void shouldNotFallbackForValidationFailure() {
        XhsPublishExecutionContext context = buildContext("client-xhs");
        XhsPublishRemoteResult primary = XhsPublishRemoteResult.builder()
                .status("failed")
                .errorCode("TITLE_TOO_LONG")
                .errorType("validation")
                .errorMessage("title too long")
                .resultJson("{\"status\":\"failed\",\"error_code\":\"TITLE_TOO_LONG\",\"error_type\":\"validation\",\"executor\":\"backend_mcp\"}")
                .executor("backend_mcp")
                .build();

        when(remoteExecutor.executeImagePublish(anyString())).thenReturn(primary);

        XhsPublishRemoteResult result = remoteNode.executeRemoteWithFallback(context);

        assertSame(primary, result);
        verify(agentFallbackService, never()).execute(context);
        assertTrue(!remoteNode.shouldAgentFallback(primary, context));
    }

    private XhsPublishExecutionContext buildContext(String clientId) {
        JSONObject ext = new JSONObject();
        ext.put("clientId", clientId);
        JSONObject sourceContext = new JSONObject();
        sourceContext.put("ext", ext);
        return XhsPublishExecutionContext.builder()
                .task(XhsPublishTaskEntity.builder().taskId("task-1").build())
                .attempt(XhsPublishAttemptEntity.builder().attemptId("attempt-1").build())
                .sourceContext(sourceContext)
                .publishRequestJson("{\"title\":\"AI 速学\",\"content\":\"正文\",\"images\":[\"F:/school/images/1.jpg\"]}")
                .build();
    }

}
