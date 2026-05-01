package com.dasi.domain.xhspublish.service.knowledge;

import com.dasi.domain.xhspublish.adapter.port.IXhsVectorStorePort;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishKnowledgeDocRepository;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishKnowledgeDocEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XhsPublishKnowledgeIngestionServiceTest {

    private XhsPublishKnowledgeIngestionService service;

    @Mock
    private IXhsPublishKnowledgeDocRepository knowledgeDocRepository;
    @Mock
    private IXhsVectorStorePort vectorStorePort;
    @Mock
    private XhsPublishIdSupport idSupport;

    @BeforeEach
    void setUp() {
        service = new XhsPublishKnowledgeIngestionService();
        ReflectionTestUtils.setField(service, "knowledgeDocRepository", knowledgeDocRepository);
        ReflectionTestUtils.setField(service, "vectorStorePort", vectorStorePort);
        ReflectionTestUtils.setField(service, "idSupport", idSupport);
    }

    @Test
    void shouldIngestPublishSuccessToPersonalKnowledge() {
        XhsPublishTaskEntity task = XhsPublishTaskEntity.builder()
                .taskId("task-1")
                .taskName("任务标题")
                .userId(1001L)
                .latestContextJson("{\"title\":\"成片标题\",\"content\":\"正文内容\",\"tags\":[\"AI\",\"运营\"]}")
                .build();
        XhsPublishAttemptEntity attempt = XhsPublishAttemptEntity.builder()
                .attemptId("attempt-1")
                .build();
        XhsPublishRemoteResult remoteResult = XhsPublishRemoteResult.builder()
                .resultJson("{\"status\":\"published\",\"postUrl\":\"https://xhs.com/post/123\"}")
                .build();
        when(idSupport.nextKnowledgeId()).thenReturn("knowledge-1");
        when(knowledgeDocRepository.queryByKnowledgeId("knowledge-1")).thenReturn(XhsPublishKnowledgeDocEntity.builder()
                .id(1L)
                .knowledgeId("knowledge-1")
                .userId(1001L)
                .vectorStatus("pending")
                .build());

        service.ingestPublishSuccess(task, attempt, remoteResult);

        verify(knowledgeDocRepository).insert(any());
        ArgumentCaptor<String> contentCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(vectorStorePort).upsert(contentCaptor.capture(), metadataCaptor.capture());
        verify(knowledgeDocRepository).update(any());
        assertTrue(contentCaptor.getValue().contains("标题：成片标题"));
        assertTrue(contentCaptor.getValue().contains("正文：正文内容"));
        assertTrue(contentCaptor.getValue().contains("发布链接：https://xhs.com/post/123"));
        assertEquals("task-1", metadataCaptor.getValue().get("taskId"));
    }

    @Test
    void shouldSkipWhenNoDigestibleContent() {
        XhsPublishTaskEntity task = XhsPublishTaskEntity.builder()
                .taskId("task-2")
                .userId(1002L)
                .latestContextJson("{\"images\":[\"a.png\"]}")
                .build();

        service.ingestPublishSuccess(task, XhsPublishAttemptEntity.builder().attemptId("attempt-2").build(),
                XhsPublishRemoteResult.builder().resultJson("{\"status\":\"published\"}").build());

        verify(knowledgeDocRepository, never()).insert(any());
        verify(vectorStorePort, never()).upsert(any(), anyMap());
    }

    @Test
    void shouldMarkFailedWhenVectorUpsertThrows() {
        XhsPublishTaskEntity task = XhsPublishTaskEntity.builder()
                .taskId("task-3")
                .taskName("任务标题")
                .userId(1003L)
                .latestContextJson("{\"title\":\"标题\",\"content\":\"正文\"}")
                .build();
        XhsPublishAttemptEntity attempt = XhsPublishAttemptEntity.builder().attemptId("attempt-3").build();
        when(idSupport.nextKnowledgeId()).thenReturn("knowledge-3");
        when(knowledgeDocRepository.queryByKnowledgeId("knowledge-3")).thenReturn(XhsPublishKnowledgeDocEntity.builder()
                .id(3L)
                .knowledgeId("knowledge-3")
                .vectorStatus("pending")
                .build());
        org.mockito.Mockito.doThrow(new RuntimeException("vector unavailable"))
                .when(vectorStorePort).upsert(any(), anyMap());

        service.ingestPublishSuccess(task, attempt, XhsPublishRemoteResult.builder()
                .resultJson("{\"status\":\"published\"}")
                .build());

        ArgumentCaptor<XhsPublishKnowledgeDocEntity> captor = ArgumentCaptor.forClass(XhsPublishKnowledgeDocEntity.class);
        verify(knowledgeDocRepository).update(captor.capture());
        assertEquals("failed", captor.getValue().getVectorStatus());
        verify(vectorStorePort).upsert(eq("标题：标题\n正文：正文"), anyMap());
    }
}
