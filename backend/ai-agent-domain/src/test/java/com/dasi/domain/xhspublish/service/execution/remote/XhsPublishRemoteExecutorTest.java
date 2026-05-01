package com.dasi.domain.xhspublish.service.execution.remote;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.adapter.port.IXhsMcpPort;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XhsPublishRemoteExecutorTest {

    private XhsPublishRemoteExecutor remoteExecutor;

    @Mock
    private IXhsMcpPort xhsMcpPort;

    private IXhsPublishConfigRepository configRepository;

    @BeforeEach
    void setUp() {
        remoteExecutor = new XhsPublishRemoteExecutor();
        configRepository = new IXhsPublishConfigRepository() {
            @Override public String getAssetBaseDir() { return null; }
            @Override public String getAssetAccessBaseUrl() { return null; }
            @Override public String getSnapshotBaseDir() { return null; }
            @Override public Integer getAssetRetentionHours() { return null; }
            @Override public Integer getSnapshotRetentionHours() { return null; }
            @Override public String getDefaultTenantId() { return null; }
            @Override public String getDefaultAccountId() { return null; }
            @Override public String getDefaultAccountName() { return null; }
            @Override public String getVectorSchemaName() { return null; }
            @Override public String getVectorTableName() { return null; }
            @Override public Integer getMcpPollRounds() { return 1; }
            @Override public Integer getMcpPollIntervalMillis() { return 0; }
        };

        ReflectionTestUtils.setField(remoteExecutor, "xhsMcpPort", xhsMcpPort);
        ReflectionTestUtils.setField(remoteExecutor, "xhsPublishConfigRepository", configRepository);
    }

    @Test
    void shouldClassifyValidationFailureAndSanitizeMessage() {
        when(xhsMcpPort.submitImagePublish(anyString())).thenReturn(
                "{\"status\":\"failed\",\"error_code\":\"TITLE_TOO_LONG\",\"error_message\":\"line1\\nline2\"}"
        );

        XhsPublishRemoteResult result = remoteExecutor.executeImagePublish("{\"title\":\"t\"}");

        assertNotNull(result);
        assertTrue(result.isFailed());
        assertEquals("TITLE_TOO_LONG", result.getErrorCode());
        assertEquals("validation", result.getErrorType());
        assertEquals("line1 line2", result.getErrorMessage());

        JSONObject resultView = JSON.parseObject(result.getResultJson());
        assertEquals("failed", resultView.getString("status"));
        assertEquals("TITLE_TOO_LONG", resultView.getString("error_code"));
        assertEquals("validation", resultView.getString("error_type"));
        assertEquals("backend_mcp", resultView.getString("executor"));
        assertFalse(resultView.containsKey("publish_request_json"));
    }

    @Test
    void shouldClassifyTransientWhenRemoteThrowsException() {
        String longMessage = "network timeout ".repeat(40);
        when(xhsMcpPort.submitImagePublish(anyString())).thenThrow(new RuntimeException(longMessage));

        XhsPublishRemoteResult result = remoteExecutor.executeImagePublish("{\"title\":\"t\"}");

        assertTrue(result.isFailed());
        assertEquals("REMOTE_CALL_EXCEPTION", result.getErrorCode());
        assertEquals("transient", result.getErrorType());
        assertTrue(result.getErrorMessage().length() <= 243);
        assertFalse(result.getErrorMessage().contains("\n"));
    }

    @Test
    void shouldMapPublishedWhenVerifySucceeded() {
        when(xhsMcpPort.submitImagePublish(anyString())).thenReturn(
                "{\"job_id\":\"job-1\",\"status\":\"accepted\"}"
        );
        when(xhsMcpPort.queryJobStatus("job-1")).thenReturn(
                "{\"status\":\"published\",\"note_url\":\"https://xhs.com/note/1\"}"
        );
        when(xhsMcpPort.verifyPublishedNote(anyString())).thenReturn(
                "{\"verify_status\":\"verified\",\"note_url\":\"https://xhs.com/note/1\"}"
        );

        XhsPublishRemoteResult result = remoteExecutor.executeImagePublish("{\"title\":\"t\"}");

        assertNotNull(result);
        assertTrue(result.isPublished());
        assertEquals("published", result.getStatus());
        assertNull(result.getErrorCode());
    }

}
