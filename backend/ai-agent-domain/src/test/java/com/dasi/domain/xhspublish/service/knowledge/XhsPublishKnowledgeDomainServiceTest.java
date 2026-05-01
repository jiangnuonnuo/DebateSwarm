package com.dasi.domain.xhspublish.service.knowledge;

import com.dasi.domain.xhspublish.adapter.port.IXhsVectorStorePort;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishKnowledgeDocRepository;
import com.dasi.domain.xhspublish.model.dto.UploadXhsPublishKnowledgeDTO;
import com.dasi.domain.xhspublish.model.entity.XhsPublishKnowledgeDocEntity;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskAccessSupport;
import com.dasi.types.exception.WorkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XhsPublishKnowledgeDomainServiceTest {

    private XhsPublishKnowledgeDomainService service;

    @Mock
    private IXhsPublishKnowledgeDocRepository knowledgeDocRepository;
    @Mock
    private IXhsVectorStorePort vectorStorePort;
    @Mock
    private XhsPublishTaskAccessSupport taskAccessSupport;
    @Mock
    private XhsPublishIdSupport idSupport;
    @Mock
    private TokenTextSplitter tokenTextSplitter;

    @BeforeEach
    void setUp() {
        service = new XhsPublishKnowledgeDomainService();
        ReflectionTestUtils.setField(service, "knowledgeDocRepository", knowledgeDocRepository);
        ReflectionTestUtils.setField(service, "vectorStorePort", vectorStorePort);
        ReflectionTestUtils.setField(service, "taskAccessSupport", taskAccessSupport);
        ReflectionTestUtils.setField(service, "idSupport", idSupport);
        ReflectionTestUtils.setField(service, "tokenTextSplitter", tokenTextSplitter);
    }

    @Test
    void shouldRejectSharedKnowledgeWriteByNonAdmin() {
        when(taskAccessSupport.requireUserId()).thenReturn(1001L);
        when(taskAccessSupport.requireUserRole()).thenReturn("user");

        WorkException exception = assertThrows(WorkException.class, () -> service.uploadKnowledge(
                UploadXhsPublishKnowledgeDTO.builder()
                        .title("知识标题")
                        .ragTag("xhs")
                        .knowledgeScope("shared")
                        .knowledgeType("guide")
                        .sourceType("text")
                        .summary("内容")
                        .build(),
                List.of()
        ));
        assertEquals("共享知识库仅管理员可写", exception.getMessage());
    }

    @Test
    void shouldUpsertSummaryAndMarkVectorSuccess() {
        when(taskAccessSupport.requireUserId()).thenReturn(1001L);
        when(idSupport.nextKnowledgeId()).thenReturn("knowledge-1");
        when(knowledgeDocRepository.queryByKnowledgeId("knowledge-1")).thenReturn(XhsPublishKnowledgeDocEntity.builder()
                .knowledgeId("knowledge-1")
                .userId(1001L)
                .vectorStatus("pending")
                .build());
        doNothing().when(vectorStorePort).upsert(any(), anyMap());
        doNothing().when(knowledgeDocRepository).insert(any());
        doNothing().when(knowledgeDocRepository).update(any());

        service.uploadKnowledge(UploadXhsPublishKnowledgeDTO.builder()
                .title("知识标题")
                .ragTag("xhs")
                .knowledgeScope("personal")
                .knowledgeType("guide")
                .sourceType("text")
                .summary("这是摘要")
                .build(), List.of());

        verify(vectorStorePort, times(1)).upsert(eq("这是摘要"), anyMap());
        ArgumentCaptor<XhsPublishKnowledgeDocEntity> updateCaptor = ArgumentCaptor.forClass(XhsPublishKnowledgeDocEntity.class);
        verify(knowledgeDocRepository, times(1)).update(updateCaptor.capture());
        assertEquals("success", updateCaptor.getValue().getVectorStatus());
        assertTrue("knowledge-1".equals(updateCaptor.getValue().getKnowledgeId()));
    }

}
