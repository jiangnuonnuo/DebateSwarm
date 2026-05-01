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
        // 步骤 1：统一收敛文件和 URL 输入，保证后续守卫链和素材登记只处理标准化数据。
        List<MultipartFile> normalizedFiles = normalizeFiles(fileList);
        List<String> normalizedUrls = normalizeUrls(request.getOriginImageUrls());

        // 步骤 2：走最小前置守卫链，只校验 client、需求和图片来源，不在 controller 里散落校验逻辑。
        guardChain.validate(IntelligentSubmitGuardContext.builder()
                .request(request)
                .fileList(normalizedFiles)
                .originImageUrls(normalizedUrls)
                .build());

        // 步骤 3：先创建 B:image 草稿任务；后续任一步失败都保留草稿，便于人工补救或再次提交。
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
            // 步骤 4：先登记素材，再把最终选中的 assetId 顺序写回上下文，避免模型自行决定图片。
            List<String> selectedAssetIds = registerMaterials(taskId, normalizedFiles, normalizedUrls);

            // 步骤 5：调用固定模板生成结构化文案参数，只生成 title/content/tags/visibility/is_original。
            GeneratedPublishContext generatedContext = generatorService.generate(request, selectedAssetIds.size());

            // 步骤 6：把“模型生成结果 + 用户显式参数 + 选中素材”收敛为 latestContextJson 真相源。
            String latestContextJson = buildLatestContextJson(request, generatedContext, selectedAssetIds);
            taskCommandDomainService.updateTaskContext(UpdateXhsPublishTaskContextDTO.builder()
                    .taskId(taskId)
                    .latestContextJson(latestContextJson)
                    .build());

            // 步骤 7：统一复用既有 submit 主链，之后进入执行树、远端发布和结果回写。
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
        // 素材顺序固定为：上传文件在前，originUrl 在后；后续 selectedAssetIds 按这个顺序直接发布。
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
        // latestContextJson 是发布执行期的唯一真相源：后端直调和 Agent fallback 都只读这里。
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
        // 覆盖顺序固定：用户显式传值 > 模型生成值 > 默认公开可见。
        if (StringUtils.hasText(request.getVisibility())) {
            return XhsPublishRuleSupport.normalizeVisibility(request.getVisibility());
        }
        if (StringUtils.hasText(generatedContext.getVisibility())) {
            return XhsPublishRuleSupport.normalizeVisibility(generatedContext.getVisibility());
        }
        return "公开可见";
    }

    private boolean resolveIsOriginal(IntelligentXhsPublishSubmitDTO request, GeneratedPublishContext generatedContext) {
        // 覆盖顺序固定：用户显式传值 > 模型生成值 > 默认 false。
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
