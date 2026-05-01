package com.dasi.domain.xhspublish.service.intelligent.guard;

import com.dasi.domain.xhspublish.model.dto.IntelligentXhsPublishSubmitDTO;
import com.dasi.domain.xhspublish.service.support.XhsPublishClientSupport;
import com.dasi.types.exception.WorkException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class XhsPublishIntelligentSubmitGuardChainTest {

    @Test
    void shouldRejectWhenClientIsUnavailable() {
        XhsPublishClientSupport clientSupport = mock(XhsPublishClientSupport.class);
        doThrow(new WorkException("发布生成 client 不存在或未启用"))
                .when(clientSupport).ensureClientAvailable("client-missing");

        ClientGuard clientGuard = new ClientGuard();
        ReflectionTestUtils.setField(clientGuard, "clientSupport", clientSupport);

        XhsPublishIntelligentSubmitGuardChain chain = new XhsPublishIntelligentSubmitGuardChain(List.of(
                new ImageSourceGuard(),
                new RequirementGuard(),
                clientGuard
        ));

        IntelligentXhsPublishSubmitDTO request = IntelligentXhsPublishSubmitDTO.builder()
                .taskName("任务")
                .clientId("client-missing")
                .publishRequirement("生成文案")
                .originImageUrls(List.of("https://img.example.com/1.png"))
                .build();

        assertThrows(WorkException.class, () -> chain.validate(IntelligentSubmitGuardContext.builder()
                .request(request)
                .originImageUrls(List.of("https://img.example.com/1.png"))
                .build()));
    }

    @Test
    void shouldRejectWhenNoImageSourceProvided() {
        XhsPublishClientSupport clientSupport = mock(XhsPublishClientSupport.class);
        doNothing().when(clientSupport).ensureClientAvailable("client-1");

        ClientGuard clientGuard = new ClientGuard();
        ReflectionTestUtils.setField(clientGuard, "clientSupport", clientSupport);

        XhsPublishIntelligentSubmitGuardChain chain = new XhsPublishIntelligentSubmitGuardChain(List.of(
                new RequirementGuard(),
                clientGuard,
                new ImageSourceGuard()
        ));

        IntelligentXhsPublishSubmitDTO request = IntelligentXhsPublishSubmitDTO.builder()
                .taskName("任务")
                .clientId("client-1")
                .publishRequirement("生成文案")
                .build();

        assertThrows(WorkException.class, () -> chain.validate(IntelligentSubmitGuardContext.builder()
                .request(request)
                .fileList(List.of())
                .originImageUrls(List.of())
                .build()));
    }

    @Test
    void shouldPassWhenRequirementAndImagesAreReady() {
        XhsPublishClientSupport clientSupport = mock(XhsPublishClientSupport.class);
        doNothing().when(clientSupport).ensureClientAvailable("client-2");

        ClientGuard clientGuard = new ClientGuard();
        ReflectionTestUtils.setField(clientGuard, "clientSupport", clientSupport);

        XhsPublishIntelligentSubmitGuardChain chain = new XhsPublishIntelligentSubmitGuardChain(List.of(
                new RequirementGuard(),
                clientGuard,
                new ImageSourceGuard()
        ));

        IntelligentXhsPublishSubmitDTO request = IntelligentXhsPublishSubmitDTO.builder()
                .taskName("任务")
                .clientId("client-2")
                .publishRequirement("生成文案")
                .build();
        MockMultipartFile file = new MockMultipartFile("fileList", "cover.png", "image/png", "img".getBytes());

        chain.validate(IntelligentSubmitGuardContext.builder()
                .request(request)
                .fileList(List.of(file))
                .originImageUrls(List.of())
                .build());
    }

}
