package com.dasi.trigger.controller;

import com.dasi.api.IXhsPublishApi;
import com.dasi.api.dto.request.xhspublish.*;
import com.dasi.api.dto.response.xhspublish.*;
import com.dasi.domain.xhspublish.model.aggregate.XhsPublishTaskAggregate;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishContentAssetEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishIntelligentSubmitCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishIntelligentSubmitResultEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishReviewEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTemplateEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishCreateTaskCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishKnowledgeUploadCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishMaterialUploadCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishRetryCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishReviewCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishSubmitTaskCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskPageQueryEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTemplatePageQueryEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTemplateSaveCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishUpdateContextCommandEntity;
import com.dasi.domain.xhspublish.service.intelligent.XhsPublishIntelligentSubmitDomainService;
import com.dasi.domain.xhspublish.service.knowledge.XhsPublishKnowledgeDomainService;
import com.dasi.domain.xhspublish.service.material.XhsPublishMaterialDomainService;
import com.dasi.domain.xhspublish.service.snapshot.XhsPublishSnapshotDomainService;
import com.dasi.domain.xhspublish.service.task.XhsPublishTaskCommandDomainService;
import com.dasi.domain.xhspublish.service.task.XhsPublishTaskQueryDomainService;
import com.dasi.domain.xhspublish.service.template.XhsPublishTemplateDomainService;
import com.dasi.types.exception.WorkException;
import com.dasi.types.result.PageResult;
import com.dasi.types.result.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/xhs/publish")
public class XhsPublishController implements IXhsPublishApi {

    @Resource
    private XhsPublishTaskCommandDomainService taskCommandDomainService;

    @Resource
    private XhsPublishTaskQueryDomainService taskQueryDomainService;

    @Resource
    private XhsPublishTemplateDomainService templateDomainService;

    @Resource
    private XhsPublishMaterialDomainService materialDomainService;

    @Resource
    private XhsPublishKnowledgeDomainService knowledgeDomainService;

    @Resource
    private XhsPublishSnapshotDomainService snapshotDomainService;

    @Resource
    private XhsPublishIntelligentSubmitDomainService intelligentSubmitDomainService;

    @Override
    @PostMapping("/task/create")
    public Result<String> createTask(@Valid @RequestBody CreateXhsPublishTaskRequestDTO dto) {
        log.info("【小红书发布】接收创建任务请求：{}", dto);
        XhsPublishCreateTaskCommandEntity command = XhsPublishCreateTaskCommandEntity.builder()
                .taskName(dto.getTaskName())
                .publishMode(dto.getPublishMode())
                .publishType(dto.getPublishType())
                .bindingId(dto.getBindingId())
                .templateId(dto.getTemplateId())
                .scheduledPublishAt(dto.getScheduledPublishAt())
                .requestJson(dto.getRequestJson())
                .retryPolicyJson(dto.getRetryPolicyJson())
                .build();
        String taskId = taskCommandDomainService.createTask(command);
        return Result.success(taskId);
    }

    @Override
    @PostMapping("/task/context/update")
    public Result<Void> updateTaskContext(@Valid @RequestBody UpdateXhsPublishTaskContextRequestDTO dto) {
        log.info("【小红书发布】接收任务上下文更新请求：{}", dto);
        XhsPublishUpdateContextCommandEntity command = XhsPublishUpdateContextCommandEntity.builder()
                .taskId(dto.getTaskId())
                .latestContextJson(dto.getLatestContextJson())
                .build();
        taskCommandDomainService.updateTaskContext(command);
        return Result.success();
    }

    @Override
    @PostMapping("/task/submit")
    public Result<String> submitTask(@Valid @RequestBody SubmitXhsPublishTaskRequestDTO dto) {
        log.info("【小红书发布】接收任务提交请求：{}", dto);
        XhsPublishSubmitTaskCommandEntity command = XhsPublishSubmitTaskCommandEntity.builder()
                .taskId(dto.getTaskId())
                .bindingId(dto.getBindingId())
                .overrideContextJson(dto.getOverrideContextJson())
                .triggerType(dto.getTriggerType())
                .build();
        String attemptId = taskCommandDomainService.submitTask(command);
        return Result.success(attemptId);
    }

    @Override
    @PostMapping("/task/page")
    public Result<PageResult<XhsPublishTaskPageResponseDTO>> pageTask(@Valid @RequestBody PageXhsPublishTaskRequestDTO dto) {
        log.info("【小红书发布】接收任务分页请求：{}", dto);
        XhsPublishTaskPageQueryEntity query = XhsPublishTaskPageQueryEntity.builder()
                .pageNum(dto.getPageNum())
                .pageSize(dto.getPageSize())
                .keyword(dto.getKeyword())
                .taskStatus(dto.getTaskStatus())
                .currentStage(dto.getCurrentStage())
                .build();
        PageResult<XhsPublishTaskEntity> pageResult = taskQueryDomainService.pageTask(query);
        List<XhsPublishTaskPageResponseDTO> responseList = pageResult.getList().stream()
                .map(entity -> XhsPublishTaskPageResponseDTO.builder()
                        .taskId(entity.getTaskId())
                        .taskName(entity.getTaskName())
                        .publishMode(entity.getPublishMode())
                        .publishType(entity.getPublishType())
                        .taskStatus(entity.getTaskStatus())
                        .currentStage(entity.getCurrentStage())
                        .scheduledPublishAt(entity.getScheduledPublishAt())
                        .updateTime(entity.getUpdateTime())
                        .build())
                .toList();
        PageResult<XhsPublishTaskPageResponseDTO> response = PageResult.<XhsPublishTaskPageResponseDTO>builder()
                .list(responseList)
                .total(pageResult.getTotal())
                .pageNum(pageResult.getPageNum())
                .pageSize(pageResult.getPageSize())
                .pageSum(pageResult.getPageSum())
                .build();
        return Result.success(response);
    }

    @Override
    @PostMapping("/task/detail")
    public Result<XhsPublishTaskDetailResponseDTO> detailTask(@NotBlank @RequestParam String taskId) {
        log.info("【小红书发布】接收任务详情请求：taskId={}", taskId);
        XhsPublishTaskAggregate aggregate = taskQueryDomainService.detailTask(taskId);
        XhsPublishTaskEntity task = aggregate.getTask();
        XhsPublishTaskDetailResponseDTO response = XhsPublishTaskDetailResponseDTO.builder()
                .taskId(task.getTaskId())
                .taskName(task.getTaskName())
                .publishMode(task.getPublishMode())
                .publishType(task.getPublishType())
                .taskStatus(task.getTaskStatus())
                .currentStage(task.getCurrentStage())
                .bindingId(task.getBindingId())
                .templateId(task.getTemplateId())
                .scheduledPublishAt(task.getScheduledPublishAt())
                .requestJson(task.getRequestJson())
                .latestContextJson(task.getLatestContextJson())
                .finalResultJson(task.getFinalResultJson())
                .retryPolicyJson(task.getRetryPolicyJson())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .attemptList(aggregate.getAttemptList().stream()
                        .map(entity -> XhsPublishAttemptResponseDTO.builder()
                                .attemptId(entity.getAttemptId())
                                .attemptNo(entity.getAttemptNo())
                                .attemptStatus(entity.getAttemptStatus())
                                .stage(entity.getStage())
                                .errorCode(entity.getErrorCode())
                                .errorMessage(entity.getErrorMessage())
                                .costAmount(entity.getCostAmount())
                                .startTime(entity.getStartTime())
                                .endTime(entity.getEndTime())
                                .build())
                        .toList())
                .reviewList(aggregate.getReviewList().stream()
                        .map(entity -> XhsPublishReviewResponseDTO.builder()
                                .reviewId(entity.getReviewId())
                                .reviewStage(entity.getReviewStage())
                                .reviewMode(entity.getReviewMode())
                                .reviewStatus(entity.getReviewStatus())
                                .reviewComment(entity.getReviewComment())
                                .decidedTime(entity.getDecidedTime())
                                .build())
                        .toList())
                .assetList(aggregate.getAssetList().stream()
                        .map(entity -> XhsPublishAssetResponseDTO.builder()
                                .assetId(entity.getAssetId())
                                .assetType(entity.getAssetType())
                                .sourceType(entity.getSourceType())
                                .storageType(entity.getStorageType())
                                .accessUrl(entity.getAccessUrl())
                                .sortNo(entity.getSortNo())
                                .assetStatus(entity.getAssetStatus())
                                .expireTime(entity.getExpireTime())
                                .build())
                        .toList())
                .build();
        return Result.success(response);
    }

    @Override
    @PostMapping("/task/review")
    public Result<Void> reviewTask(@Valid @RequestBody ReviewXhsPublishTaskRequestDTO dto) {
        log.info("【小红书发布】接收审核请求：{}", dto);
        XhsPublishReviewCommandEntity command = XhsPublishReviewCommandEntity.builder()
                .taskId(dto.getTaskId())
                .attemptId(dto.getAttemptId())
                .reviewStage(dto.getReviewStage())
                .reviewMode(dto.getReviewMode())
                .reviewStatus(dto.getReviewStatus())
                .reviewComment(dto.getReviewComment())
                .reviewPayloadJson(dto.getReviewPayloadJson())
                .decisionJson(dto.getDecisionJson())
                .build();
        taskCommandDomainService.reviewTask(command);
        return Result.success();
    }

    @Override
    @PostMapping("/task/retry")
    public Result<Void> retryTask(@Valid @RequestBody RetryXhsPublishTaskRequestDTO dto) {
        log.info("【小红书发布】接收重试请求：{}", dto);
        XhsPublishRetryCommandEntity command = XhsPublishRetryCommandEntity.builder()
                .taskId(dto.getTaskId())
                .overrideContextJson(dto.getOverrideContextJson())
                .triggerType(dto.getTriggerType())
                .build();
        taskCommandDomainService.retryTask(command);
        return Result.success();
    }

    @Override
    @PostMapping("/template/save")
    public Result<Void> saveTemplate(@Valid @RequestBody SaveXhsPublishTemplateRequestDTO dto) {
        log.info("【小红书发布】接收模板保存请求：{}", dto);
        XhsPublishTemplateSaveCommandEntity command = XhsPublishTemplateSaveCommandEntity.builder()
                .templateId(dto.getTemplateId())
                .templateName(dto.getTemplateName())
                .publishMode(dto.getPublishMode())
                .templateConfigJson(dto.getTemplateConfigJson())
                .templateStatus(dto.getTemplateStatus())
                .isDefault(dto.getIsDefault())
                .build();
        templateDomainService.saveTemplate(command);
        return Result.success();
    }

    @Override
    @PostMapping("/template/page")
    public Result<PageResult<XhsPublishTemplateResponseDTO>> pageTemplate(@Valid @RequestBody PageXhsPublishTemplateRequestDTO dto) {
        log.info("【小红书发布】接收模板分页请求：{}", dto);
        XhsPublishTemplatePageQueryEntity query = XhsPublishTemplatePageQueryEntity.builder()
                .pageNum(dto.getPageNum())
                .pageSize(dto.getPageSize())
                .keyword(dto.getKeyword())
                .build();
        PageResult<XhsPublishTemplateEntity> pageResult = templateDomainService.pageTemplate(query);
        List<XhsPublishTemplateResponseDTO> responseList = pageResult.getList().stream()
                .map(entity -> XhsPublishTemplateResponseDTO.builder()
                        .templateId(entity.getTemplateId())
                        .templateName(entity.getTemplateName())
                        .publishMode(entity.getPublishMode())
                        .templateStatus(entity.getTemplateStatus())
                        .isDefault(entity.getIsDefault())
                        .updateTime(entity.getUpdateTime())
                        .build())
                .toList();
        PageResult<XhsPublishTemplateResponseDTO> response = PageResult.<XhsPublishTemplateResponseDTO>builder()
                .list(responseList)
                .total(pageResult.getTotal())
                .pageNum(pageResult.getPageNum())
                .pageSize(pageResult.getPageSize())
                .pageSum(pageResult.getPageSum())
                .build();
        return Result.success(response);
    }

    @Override
    @PostMapping("/material/upload")
    public Result<List<XhsPublishAssetResponseDTO>> uploadMaterial(@Valid @ModelAttribute UploadXhsPublishMaterialRequestDTO dto,
                                                          @RequestPart(value = "fileList", required = false) List<MultipartFile> fileList) {
        List<MultipartFile> safeFileList = normalizeFileList(fileList);
        validateUploadMaterialRequest(dto, safeFileList);

        int fileCount = safeFileList.size();
        log.info("【小红书发布】接收素材上传请求：taskId={}, sourceType={}, fileCount={}, hasOriginUrl={}",
                dto.getTaskId(), dto.getSourceType(), fileCount, StringUtils.hasText(dto.getOriginUrl()));

        XhsPublishMaterialUploadCommandEntity command = XhsPublishMaterialUploadCommandEntity.builder()
                .taskId(dto.getTaskId())
                .attemptId(dto.getAttemptId())
                .assetType(dto.getAssetType())
                .sourceType(dto.getSourceType())
                .originUrl(dto.getOriginUrl())
                .sortNo(dto.getSortNo())
                .build();
        List<XhsPublishContentAssetEntity> assetList = materialDomainService.uploadMaterial(command, safeFileList);
        List<XhsPublishAssetResponseDTO> response = assetList.stream()
                .map(entity -> XhsPublishAssetResponseDTO.builder()
                        .assetId(entity.getAssetId())
                        .assetType(entity.getAssetType())
                        .sourceType(entity.getSourceType())
                        .storageType(entity.getStorageType())
                        .accessUrl(entity.getAccessUrl())
                        .sortNo(entity.getSortNo())
                        .assetStatus(entity.getAssetStatus())
                        .expireTime(entity.getExpireTime())
                        .build())
                .toList();
        return Result.success(response);
    }

    @Override
    @PostMapping("/knowledge/upload")
    public Result<Void> uploadKnowledge(@Valid @ModelAttribute UploadXhsPublishKnowledgeRequestDTO dto,
                                        @RequestPart(value = "fileList", required = false) List<MultipartFile> fileList) {
        List<MultipartFile> safeFileList = normalizeFileList(fileList);
        log.info("【小红书发布】接收知识上传请求：title={}, scope={}, fileCount={}",
                dto.getTitle(), dto.getKnowledgeScope(), safeFileList.size());
        XhsPublishKnowledgeUploadCommandEntity command = XhsPublishKnowledgeUploadCommandEntity.builder()
                .knowledgeScope(dto.getKnowledgeScope())
                .knowledgeType(dto.getKnowledgeType())
                .title(dto.getTitle())
                .ragTag(dto.getRagTag())
                .sourceType(dto.getSourceType())
                .sourceRef(dto.getSourceRef())
                .summary(dto.getSummary())
                .build();
        knowledgeDomainService.uploadKnowledge(command, safeFileList);
        return Result.success();
    }

    @Override
    @PostMapping("/task/intelligent/submit")
    public Result<IntelligentXhsPublishSubmitResponseDTO> submitIntelligentTask(@Valid @ModelAttribute IntelligentXhsPublishSubmitRequestDTO dto,
                                                                       @RequestPart(value = "fileList", required = false) List<MultipartFile> fileList) {
        List<MultipartFile> safeFileList = normalizeFileList(fileList);
        int fileCount = safeFileList.size();
        int urlCount = countValidUrls(dto.getOriginImageUrls());

        validateIntelligentSubmitRequest(dto, fileCount, urlCount);

        log.info("【小红书发布】接收一键智能发布请求：taskName={}, clientId={}, fileCount={}, urlCount={}",
                dto.getTaskName(), dto.getClientId(), fileCount, urlCount);

        XhsPublishIntelligentSubmitCommandEntity command = XhsPublishIntelligentSubmitCommandEntity.builder()
                .taskName(dto.getTaskName())
                .clientId(dto.getClientId())
                .publishRequirement(dto.getPublishRequirement())
                .bindingId(dto.getBindingId())
                .visibility(dto.getVisibility())
                .isOriginal(dto.getIsOriginal())
                .scheduledPublishAt(dto.getScheduledPublishAt())
                .originImageUrls(dto.getOriginImageUrls())
                .build();
        XhsPublishIntelligentSubmitResultEntity result = intelligentSubmitDomainService.submit(command, safeFileList);
        IntelligentXhsPublishSubmitResponseDTO response = IntelligentXhsPublishSubmitResponseDTO.builder()
                .taskId(result.getTaskId())
                .attemptId(result.getAttemptId())
                .build();
        return Result.success(response);
    }

    @GetMapping("/material/access")
    public ResponseEntity<org.springframework.core.io.Resource> accessMaterial(@NotBlank @RequestParam String assetId) {
        org.springframework.core.io.Resource resource = materialDomainService.accessMaterial(assetId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    private void validateUploadMaterialRequest(UploadXhsPublishMaterialRequestDTO dto, List<MultipartFile> fileList) {
        boolean hasFiles = !fileList.isEmpty();
        boolean hasOriginUrl = StringUtils.hasText(dto.getOriginUrl());
        if (!hasFiles && !hasOriginUrl) {
            throw new WorkException("素材上传至少需要文件或 originUrl");
        }
    }

    private void validateIntelligentSubmitRequest(IntelligentXhsPublishSubmitRequestDTO dto, int fileCount, int urlCount) {
        if (!StringUtils.hasText(dto.getTaskName())) {
            throw new WorkException("任务名称不能为空");
        }
        if (!StringUtils.hasText(dto.getClientId())) {
            throw new WorkException("发布生成 clientId 不能为空");
        }
        if (!StringUtils.hasText(dto.getPublishRequirement())) {
            throw new WorkException("发布需求不能为空");
        }
        if (fileCount <= 0 && urlCount <= 0) {
            throw new WorkException("一键智能发布至少需要 1 张图片，请上传文件或提供远程图片地址");
        }
    }

    private List<MultipartFile> normalizeFileList(List<MultipartFile> fileList) {
        if (fileList == null || fileList.isEmpty()) {
            return Collections.emptyList();
        }
        return fileList.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
    }

    private int countValidUrls(List<String> urlList) {
        if (urlList == null || urlList.isEmpty()) {
            return 0;
        }
        return (int) urlList.stream()
                .filter(StringUtils::hasText)
                .count();
    }

}
