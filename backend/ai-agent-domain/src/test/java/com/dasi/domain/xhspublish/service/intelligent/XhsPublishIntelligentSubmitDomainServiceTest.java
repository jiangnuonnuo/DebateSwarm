package com.dasi.domain.xhspublish.service.intelligent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.model.dto.CreateXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.IntelligentXhsPublishSubmitDTO;
import com.dasi.domain.xhspublish.model.dto.SubmitXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.UpdateXhsPublishTaskContextDTO;
import com.dasi.domain.xhspublish.model.dto.UploadXhsPublishMaterialDTO;
import com.dasi.domain.xhspublish.model.vo.IntelligentXhsPublishSubmitVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishAssetVO;
import com.dasi.domain.xhspublish.service.generator.GeneratedPublishContext;
import com.dasi.domain.xhspublish.service.generator.IXhsPublishGeneratorService;
import com.dasi.domain.xhspublish.service.intelligent.guard.XhsPublishIntelligentSubmitGuardChain;
import com.dasi.domain.xhspublish.service.material.XhsPublishMaterialDomainService;
import com.dasi.domain.xhspublish.service.task.XhsPublishTaskCommandDomainService;
import com.dasi.types.exception.WorkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XhsPublishIntelligentSubmitDomainServiceTest {

    private XhsPublishIntelligentSubmitDomainService service;

    @Mock
    private XhsPublishIntelligentSubmitGuardChain guardChain;
    @Mock
    private XhsPublishTaskCommandDomainService taskCommandDomainService;
    @Mock
    private XhsPublishMaterialDomainService materialDomainService;
    @Mock
    private IXhsPublishGeneratorService generatorService;

    @BeforeEach
    void setUp() {
        service = new XhsPublishIntelligentSubmitDomainService();
        ReflectionTestUtils.setField(service, "guardChain", guardChain);
        ReflectionTestUtils.setField(service, "taskCommandDomainService", taskCommandDomainService);
        ReflectionTestUtils.setField(service, "materialDomainService", materialDomainService);
        ReflectionTestUtils.setField(service, "generatorService", generatorService);
    }

    @Test
    void shouldSubmitIntelligentTaskWithOriginUrlsOnly() {
        IntelligentXhsPublishSubmitDTO request = IntelligentXhsPublishSubmitDTO.builder()
                .taskName("春日穿搭")
                .clientId("client-1")
                .publishRequirement("写一篇春日通勤穿搭的小红书图文")
                .originImageUrls(List.of("https://img.example.com/1.png"))
                .build();
        when(taskCommandDomainService.createTask(any(CreateXhsPublishTaskDTO.class))).thenReturn("task-1");
        when(materialDomainService.uploadMaterial(any(UploadXhsPublishMaterialDTO.class), eq(null)))
                .thenReturn(List.of(asset("asset-1")));
        when(generatorService.generate(eq(request), eq(1))).thenReturn(GeneratedPublishContext.builder()
                .title("通勤穿搭")
                .content("今天分享一套轻松通勤穿搭。")
                .tags(List.of("通勤穿搭", "春日搭配"))
                .visibility("公开可见")
                .isOriginal(true)
                .build());
        when(taskCommandDomainService.submitTask(any(SubmitXhsPublishTaskDTO.class))).thenReturn("attempt-1");

        IntelligentXhsPublishSubmitVO result = service.submit(request, null);

        assertEquals("task-1", result.getTaskId());
        assertEquals("attempt-1", result.getAttemptId());

        ArgumentCaptor<CreateXhsPublishTaskDTO> createCaptor = ArgumentCaptor.forClass(CreateXhsPublishTaskDTO.class);
        verify(taskCommandDomainService).createTask(createCaptor.capture());
        assertEquals("B", createCaptor.getValue().getPublishMode());
        assertEquals("image", createCaptor.getValue().getPublishType());
        assertEquals("{}", createCaptor.getValue().getRequestJson());

        ArgumentCaptor<UpdateXhsPublishTaskContextDTO> updateCaptor = ArgumentCaptor.forClass(UpdateXhsPublishTaskContextDTO.class);
        verify(taskCommandDomainService).updateTaskContext(updateCaptor.capture());
        JSONObject context = JSON.parseObject(updateCaptor.getValue().getLatestContextJson());
        assertEquals("通勤穿搭", context.getString("title"));
        assertEquals(List.of("asset-1"), context.getList("selectedAssetIds", String.class));
        assertEquals("公开可见", context.getString("visibility"));
        assertTrue(context.getBooleanValue("is_original"));
    }

    @Test
    void shouldSubmitIntelligentTaskWithFilesOnly() {
        IntelligentXhsPublishSubmitDTO request = IntelligentXhsPublishSubmitDTO.builder()
                .taskName("家居好物")
                .clientId("client-2")
                .publishRequirement("生成一篇家居好物推荐文案")
                .build();
        MockMultipartFile file = new MockMultipartFile("fileList", "cover.png", "image/png", "img".getBytes());
        when(taskCommandDomainService.createTask(any(CreateXhsPublishTaskDTO.class))).thenReturn("task-2");
        when(materialDomainService.uploadMaterial(any(UploadXhsPublishMaterialDTO.class), any()))
                .thenReturn(List.of(asset("asset-file-1")));
        when(generatorService.generate(eq(request), eq(1))).thenReturn(GeneratedPublishContext.builder()
                .title("家居好物")
                .content("把日常幸福感装进家里。")
                .tags(List.of("家居好物"))
                .build());
        when(taskCommandDomainService.submitTask(any(SubmitXhsPublishTaskDTO.class))).thenReturn("attempt-2");

        IntelligentXhsPublishSubmitVO result = service.submit(request, List.of(file));

        assertEquals("task-2", result.getTaskId());
        ArgumentCaptor<UploadXhsPublishMaterialDTO> uploadCaptor = ArgumentCaptor.forClass(UploadXhsPublishMaterialDTO.class);
        verify(materialDomainService).uploadMaterial(uploadCaptor.capture(), any());
        assertEquals("intelligent_upload", uploadCaptor.getValue().getSourceType());
    }

    @Test
    void shouldPreserveImageOrderWhenMixingFilesAndOriginUrls() {
        IntelligentXhsPublishSubmitDTO request = IntelligentXhsPublishSubmitDTO.builder()
                .taskName("露营清单")
                .clientId("client-3")
                .publishRequirement("输出一篇露营清单图文")
                .originImageUrls(List.of("https://img.example.com/3.png"))
                .build();
        MockMultipartFile file1 = new MockMultipartFile("fileList", "1.png", "image/png", "1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("fileList", "2.png", "image/png", "2".getBytes());
        when(taskCommandDomainService.createTask(any(CreateXhsPublishTaskDTO.class))).thenReturn("task-3");
        when(materialDomainService.uploadMaterial(any(UploadXhsPublishMaterialDTO.class), any()))
                .thenReturn(List.of(asset("asset-1")))
                .thenReturn(List.of(asset("asset-2")));
        when(materialDomainService.uploadMaterial(any(UploadXhsPublishMaterialDTO.class), eq(null)))
                .thenReturn(List.of(asset("asset-3")));
        when(generatorService.generate(eq(request), eq(3))).thenReturn(GeneratedPublishContext.builder()
                .title("露营清单")
                .content("周末露营别忘了这些装备。")
                .build());
        when(taskCommandDomainService.submitTask(any(SubmitXhsPublishTaskDTO.class))).thenReturn("attempt-3");

        service.submit(request, List.of(file1, file2));

        ArgumentCaptor<UpdateXhsPublishTaskContextDTO> updateCaptor = ArgumentCaptor.forClass(UpdateXhsPublishTaskContextDTO.class);
        verify(taskCommandDomainService).updateTaskContext(updateCaptor.capture());
        JSONObject context = JSON.parseObject(updateCaptor.getValue().getLatestContextJson());
        assertEquals(List.of("asset-1", "asset-2", "asset-3"), context.getList("selectedAssetIds", String.class));
    }

    @Test
    void shouldKeepDraftWhenGenerationFailsBeforeSubmit() {
        IntelligentXhsPublishSubmitDTO request = IntelligentXhsPublishSubmitDTO.builder()
                .taskName("咖啡分享")
                .clientId("client-4")
                .publishRequirement("写一篇咖啡测评")
                .originImageUrls(List.of("https://img.example.com/4.png"))
                .build();
        when(taskCommandDomainService.createTask(any(CreateXhsPublishTaskDTO.class))).thenReturn("task-4");
        when(materialDomainService.uploadMaterial(any(UploadXhsPublishMaterialDTO.class), eq(null)))
                .thenReturn(List.of(asset("asset-4")));
        when(generatorService.generate(eq(request), eq(1))).thenThrow(new WorkException("智能生成结果不是合法 JSON"));

        assertThrows(WorkException.class, () -> service.submit(request, null));

        verify(taskCommandDomainService, never()).updateTaskContext(any(UpdateXhsPublishTaskContextDTO.class));
        verify(taskCommandDomainService, never()).submitTask(any(SubmitXhsPublishTaskDTO.class));
    }

    @Test
    void shouldPreferExplicitRequestValuesOverGeneratedContext() {
        LocalDateTime scheduledAt = LocalDateTime.of(2026, 5, 1, 12, 0);
        IntelligentXhsPublishSubmitDTO request = IntelligentXhsPublishSubmitDTO.builder()
                .taskName("节日礼物")
                .clientId("client-5")
                .publishRequirement("写一篇节日送礼推荐")
                .visibility("仅自己可见")
                .isOriginal(true)
                .scheduledPublishAt(scheduledAt)
                .originImageUrls(List.of("https://img.example.com/5.png"))
                .build();
        when(taskCommandDomainService.createTask(any(CreateXhsPublishTaskDTO.class))).thenReturn("task-5");
        when(materialDomainService.uploadMaterial(any(UploadXhsPublishMaterialDTO.class), eq(null)))
                .thenReturn(List.of(asset("asset-5")));
        when(generatorService.generate(eq(request), eq(1))).thenReturn(GeneratedPublishContext.builder()
                .title("送礼推荐")
                .content("这份礼物清单真的很能打。")
                .visibility("公开可见")
                .isOriginal(false)
                .build());
        when(taskCommandDomainService.submitTask(any(SubmitXhsPublishTaskDTO.class))).thenReturn("attempt-5");

        service.submit(request, null);

        ArgumentCaptor<UpdateXhsPublishTaskContextDTO> updateCaptor = ArgumentCaptor.forClass(UpdateXhsPublishTaskContextDTO.class);
        verify(taskCommandDomainService).updateTaskContext(updateCaptor.capture());
        JSONObject context = JSON.parseObject(updateCaptor.getValue().getLatestContextJson());
        assertEquals("仅自己可见", context.getString("visibility"));
        assertTrue(context.getBooleanValue("is_original"));
        assertTrue(context.getString("schedule_at").startsWith("2026-05-01T12:00"));
    }

    private XhsPublishAssetVO asset(String assetId) {
        return XhsPublishAssetVO.builder()
                .assetId(assetId)
                .assetStatus("ready")
                .build();
    }

}
