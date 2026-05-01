package com.dasi.domain.xhspublish.service.template;

import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishTemplateRepository;
import com.dasi.domain.xhspublish.model.dto.SaveXhsPublishTemplateDTO;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTemplateEntity;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskAccessSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishViewAssembler;
import com.dasi.types.exception.WorkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XhsPublishTemplateDomainServiceTest {

    private XhsPublishTemplateDomainService service;

    @Mock
    private IXhsPublishTemplateRepository templateRepository;
    @Mock
    private XhsPublishTaskAccessSupport taskAccessSupport;
    @Mock
    private XhsPublishIdSupport idSupport;

    @BeforeEach
    void setUp() {
        service = new XhsPublishTemplateDomainService();
        ReflectionTestUtils.setField(service, "templateRepository", templateRepository);
        ReflectionTestUtils.setField(service, "taskAccessSupport", taskAccessSupport);
        ReflectionTestUtils.setField(service, "idSupport", idSupport);
        ReflectionTestUtils.setField(service, "viewAssembler", new XhsPublishViewAssembler());
    }

    @Test
    void shouldInsertTemplateWhenTemplateIdIsEmpty() {
        when(taskAccessSupport.requireUserId()).thenReturn(1001L);
        when(idSupport.nextTemplateId()).thenReturn("tpl-1");
        doNothing().when(templateRepository).insert(org.mockito.ArgumentMatchers.any());

        service.saveTemplate(SaveXhsPublishTemplateDTO.builder()
                .templateName("模板1")
                .publishMode("B")
                .templateConfigJson("{\"title\":\"A\"}")
                .templateStatus(1)
                .isDefault(0)
                .build());

        ArgumentCaptor<XhsPublishTemplateEntity> captor = ArgumentCaptor.forClass(XhsPublishTemplateEntity.class);
        verify(templateRepository, times(1)).insert(captor.capture());
        assertEquals("tpl-1", captor.getValue().getTemplateId());
        assertEquals(1001L, captor.getValue().getUserId());
    }

    @Test
    void shouldUpdateOwnedTemplate() {
        when(taskAccessSupport.requireUserId()).thenReturn(1001L);
        when(templateRepository.queryByTemplateId("tpl-1")).thenReturn(XhsPublishTemplateEntity.builder()
                .templateId("tpl-1")
                .userId(1001L)
                .templateName("旧模板")
                .build());
        doNothing().when(templateRepository).update(org.mockito.ArgumentMatchers.any());

        service.saveTemplate(SaveXhsPublishTemplateDTO.builder()
                .templateId("tpl-1")
                .templateName("新模板")
                .publishMode("A")
                .templateConfigJson("{\"title\":\"B\"}")
                .templateStatus(1)
                .isDefault(1)
                .build());

        ArgumentCaptor<XhsPublishTemplateEntity> captor = ArgumentCaptor.forClass(XhsPublishTemplateEntity.class);
        verify(templateRepository, times(1)).update(captor.capture());
        assertEquals("新模板", captor.getValue().getTemplateName());
        assertEquals("A", captor.getValue().getPublishMode());
    }

    @Test
    void shouldRejectUpdateWhenTemplateNotOwned() {
        when(taskAccessSupport.requireUserId()).thenReturn(1001L);
        when(templateRepository.queryByTemplateId("tpl-1")).thenReturn(XhsPublishTemplateEntity.builder()
                .templateId("tpl-1")
                .userId(2002L)
                .build());

        WorkException exception = assertThrows(WorkException.class, () -> service.saveTemplate(SaveXhsPublishTemplateDTO.builder()
                .templateId("tpl-1")
                .templateName("新模板")
                .publishMode("A")
                .templateConfigJson("{}")
                .build()));
        assertEquals("模板不存在", exception.getMessage());
    }

}
