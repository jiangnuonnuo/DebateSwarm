package com.dasi.domain.xhspublish.service.intelligent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.model.dto.CreateXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.IntelligentXhsPublishSubmitDTO;
import com.dasi.domain.xhspublish.model.dto.SubmitXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.UpdateXhsPublishTaskContextDTO;
import com.dasi.domain.xhspublish.model.dto.UploadXhsPublishMaterialDTO;
import com.dasi.domain.xhspublish.model.valobj.PublishModeVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTypeVO;
import com.dasi.domain.xhspublish.model.vo.IntelligentXhsPublishSubmitVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishAssetVO;
import com.dasi.domain.xhspublish.service.generator.GeneratedPublishContext;
import com.dasi.domain.xhspublish.service.generator.IXhsPublishGeneratorService;
import com.dasi.domain.xhspublish.service.intelligent.guard.IntelligentSubmitGuardContext;
import com.dasi.domain.xhspublish.service.intelligent.guard.XhsPublishIntelligentSubmitGuardChain;
import com.dasi.domain.xhspublish.service.material.XhsPublishMaterialDomainService;
import com.dasi.domain.xhspublish.service.rule.XhsPublishRuleSupport;
import com.dasi.domain.xhspublish.service.task.XhsPublishTaskCommandDomainService;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Slf4j
@Service
public class XhsPublishIntelligentSubmitDomainService {

    @Resource
    private XhsPublishIntelligentSubmitGuardChain guardChain;

    @Resource
    private XhsPublishTaskCommandDomainService taskCommandDomainService;

    @Resource
    private XhsPublishMaterialDomainService materialDomainService;

    @Resource
    private IXhsPublishGeneratorService generatorService;

    public IntelligentXhsPublishSubmitVO submit(IntelligentXhsPublishSubmitDTO request, List<MultipartFile> fileList) {
        List<MultipartFile> normalizedFiles = normalizeFiles(fileList);
        List<String> normalizedUrls = normalizeUrls(request.getOriginImageUrls());
        guardChain.validate(IntelligentSubmitGuardContext.builder()
                .request(request)
                .fileList(normalizedFiles)
                .originImageUrls(normalizedUrls)
                .build());

        String taskId = taskCommandDomainService.createTask(CreateXhsPublishTaskDTO.builder()
                .taskName(request.getTaskName().trim())
                .publishMode(PublishModeVO.B.name())
                .publishType(PublishTypeVO.image.name())
                .bindingId(request.getBindingId())
                .scheduledPublishAt(request.getScheduledPublishAt())
                .requestJson("{}")
                .build());

        log.info("【小红书发布】一键智能发布草稿创建完成：taskId={}, clientId={}, fileCount={}, urlCount={}",
                taskId, request.getClientId(), normalizedFiles.size(), normalizedUrls.size());

        try {
            List<String> selectedAssetIds = registerMaterials(taskId, normalizedFiles, normalizedUrls);
            GeneratedPublishContext generatedContext = generatorService.generate(request, selectedAssetIds.size());
            String latestContextJson = buildLatestContextJson(request, generatedContext, selectedAssetIds);
            taskCommandDomainService.updateTaskContext(UpdateXhsPublishTaskContextDTO.builder()
                    .taskId(taskId)
                    .latestContextJson(latestContextJson)
                    .build());
            String attemptId = taskCommandDomainService.submitTask(SubmitXhsPublishTaskDTO.builder()
                    .taskId(taskId)
                    .triggerType("intelligent_submit")
                    .build());
            return IntelligentXhsPublishSubmitVO.builder()
                    .taskId(taskId)
                    .attemptId(attemptId)
                    .build();
        } catch (RuntimeException e) {
            log.warn("【小红书发布】一键智能发布预提交阶段失败，草稿保留：taskId={}, message={}", taskId, e.getMessage());
            throw e;
        }
    }

    private List<String> registerMaterials(String taskId, List<MultipartFile> fileList, List<String> originImageUrls) {
        List<String> selectedAssetIds = new ArrayList<>();
        int sortNo = 1;
        for (MultipartFile file : fileList) {
            selectedAssetIds.add(saveSingleFile(taskId, file, sortNo++));
        }
        for (String originUrl : originImageUrls) {
            selectedAssetIds.add(saveSingleOriginUrl(taskId, originUrl, sortNo++));
        }
        return selectedAssetIds;
    }

    private String saveSingleFile(String taskId, MultipartFile file, int sortNo) {
        List<XhsPublishAssetVO> assetList = materialDomainService.uploadMaterial(UploadXhsPublishMaterialDTO.builder()
                .taskId(taskId)
                .sourceType("intelligent_upload")
                .sortNo(sortNo)
                .build(), List.of(file));
        return requireAssetId(assetList);
    }

    private String saveSingleOriginUrl(String taskId, String originUrl, int sortNo) {
        List<XhsPublishAssetVO> assetList = materialDomainService.uploadMaterial(UploadXhsPublishMaterialDTO.builder()
                .taskId(taskId)
                .sourceType("intelligent_origin_url")
                .originUrl(originUrl)
                .sortNo(sortNo)
                .build(), null);
        return requireAssetId(assetList);
    }

    private String requireAssetId(List<XhsPublishAssetVO> assetList) {
        if (assetList == null || assetList.isEmpty() || !StringUtils.hasText(assetList.get(0).getAssetId())) {
            throw new WorkException("素材登记失败");
        }
        return assetList.get(0).getAssetId();
    }

    private String buildLatestContextJson(IntelligentXhsPublishSubmitDTO request,
                                          GeneratedPublishContext generatedContext,
                                          List<String> selectedAssetIds) {
        LinkedHashMap<String, Object> contextMap = new LinkedHashMap<>();
        contextMap.put("title", generatedContext.getTitle());
        contextMap.put("content", generatedContext.getContent());
        contextMap.put("tags", generatedContext.getTags());
        contextMap.put("selectedAssetIds", selectedAssetIds);
        contextMap.put("visibility", resolveVisibility(request, generatedContext));
        contextMap.put("is_original", resolveIsOriginal(request, generatedContext));
        String scheduleAt = resolveScheduleAt(request);
        if (scheduleAt != null) {
            contextMap.put("schedule_at", scheduleAt);
        }

        JSONObject ext = new JSONObject(new LinkedHashMap<>());
        ext.put("submitMode", "intelligent_submit");
        ext.put("clientId", request.getClientId());
        ext.put("publishRequirement", request.getPublishRequirement().trim());
        contextMap.put("ext", ext);
        return JSON.toJSONString(contextMap);
    }

    private String resolveVisibility(IntelligentXhsPublishSubmitDTO request, GeneratedPublishContext generatedContext) {
        if (StringUtils.hasText(request.getVisibility())) {
            return XhsPublishRuleSupport.normalizeVisibility(request.getVisibility());
        }
        if (StringUtils.hasText(generatedContext.getVisibility())) {
            return XhsPublishRuleSupport.normalizeVisibility(generatedContext.getVisibility());
        }
        return "公开可见";
    }

    private boolean resolveIsOriginal(IntelligentXhsPublishSubmitDTO request, GeneratedPublishContext generatedContext) {
        if (request.getIsOriginal() != null) {
            return request.getIsOriginal();
        }
        if (generatedContext.getIsOriginal() != null) {
            return generatedContext.getIsOriginal();
        }
        return false;
    }

    private String resolveScheduleAt(IntelligentXhsPublishSubmitDTO request) {
        if (request.getScheduledPublishAt() == null) {
            return null;
        }
        ZoneOffset offset = OffsetDateTime.now().getOffset();
        return OffsetDateTime.of(request.getScheduledPublishAt(), offset).toString();
    }

    private List<MultipartFile> normalizeFiles(List<MultipartFile> fileList) {
        if (fileList == null || fileList.isEmpty()) {
            return List.of();
        }
        return fileList.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
    }

    private List<String> normalizeUrls(List<String> originImageUrls) {
        if (originImageUrls == null || originImageUrls.isEmpty()) {
            return List.of();
        }
        return originImageUrls.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

}
