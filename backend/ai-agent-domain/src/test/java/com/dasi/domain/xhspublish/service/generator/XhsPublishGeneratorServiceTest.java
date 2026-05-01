package com.dasi.domain.xhspublish.service.generator;

import com.dasi.domain.xhspublish.model.dto.IntelligentXhsPublishSubmitDTO;
import com.dasi.domain.xhspublish.service.support.XhsPublishClientSupport;
import com.dasi.types.exception.WorkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XhsPublishGeneratorServiceTest {

    private XhsPublishGeneratorService service;

    @Mock
    private XhsPublishClientSupport clientSupport;

    @BeforeEach
    void setUp() {
        service = new XhsPublishGeneratorService();
        ReflectionTestUtils.setField(service, "clientSupport", clientSupport);
        ReflectionTestUtils.setField(service, "generatedContentParser", new XhsPublishGeneratedContentParser());
    }

    @Test
    void shouldParseGeneratedJsonResponse() {
        IntelligentXhsPublishSubmitDTO request = IntelligentXhsPublishSubmitDTO.builder()
                .taskName("露营笔记")
                .clientId("client-1")
                .publishRequirement("生成露营图文")
                .build();
        when(clientSupport.promptOnce(eq("client-1"), any(Prompt.class))).thenReturn("""
                {
                  "title":"露营清单",
                  "content":"这份露营清单太实用了。",
                  "tags":["露营","装备"],
                  "visibility":"公开可见",
                  "is_original":true
                }
                """);

        GeneratedPublishContext result = service.generate(request, 3);

        assertEquals("露营清单", result.getTitle());
        assertEquals(List.of("露营", "装备"), result.getTags());
        assertEquals("公开可见", result.getVisibility());
        assertEquals(Boolean.TRUE, result.getIsOriginal());
    }

    @Test
    void shouldRejectNonJsonResponse() {
        IntelligentXhsPublishSubmitDTO request = IntelligentXhsPublishSubmitDTO.builder()
                .taskName("咖啡")
                .clientId("client-2")
                .publishRequirement("生成咖啡图文")
                .build();
        when(clientSupport.promptOnce(eq("client-2"), any(Prompt.class))).thenReturn("这不是 JSON");

        WorkException exception = assertThrows(WorkException.class, () -> service.generate(request, 1));
        assertEquals("智能生成结果不是合法 JSON", exception.getMessage());
    }

    @Test
    void shouldRejectWhenGeneratedTitleMissing() {
        IntelligentXhsPublishSubmitDTO request = IntelligentXhsPublishSubmitDTO.builder()
                .taskName("早餐")
                .clientId("client-3")
                .publishRequirement("生成早餐图文")
                .build();
        when(clientSupport.promptOnce(eq("client-3"), any(Prompt.class))).thenReturn("""
                {"content":"今天的早餐很满足。","tags":[]}
                """);

        WorkException exception = assertThrows(WorkException.class, () -> service.generate(request, 2));
        assertEquals("智能生成结果缺少标题", exception.getMessage());
    }

}
