package com.dasi.domain.xhspublish.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.util.jwt.UserContext;
import com.dasi.domain.xhspublish.config.XhsPublishProperties;
import com.dasi.domain.xhspublish.model.dto.*;
import com.dasi.domain.xhspublish.model.entity.*;
import com.dasi.domain.xhspublish.model.valobj.PublishModeVO;
import com.dasi.domain.xhspublish.model.valobj.PublishStageVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTaskStatusVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTypeVO;
import com.dasi.domain.xhspublish.model.vo.*;
import com.dasi.domain.xhspublish.port.IXhsAssetStoragePort;
import com.dasi.domain.xhspublish.port.IXhsVectorStorePort;
import com.dasi.domain.xhspublish.repository.*;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.domain.IPublishRetryDomainService;
import com.dasi.domain.xhspublish.service.domain.IPublishTaskStateMachine;
import com.dasi.domain.xhspublish.service.domain.IPublishValidationDomainService;
import com.dasi.domain.xhspublish.service.strategy.IXhsPublishExecuteStrategy;
import com.dasi.domain.xhspublish.service.strategy.XhsPublishExecuteStrategyFactory;
import com.dasi.domain.xhspublish.service.support.XhsPublishBindingSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import com.dasi.types.exception.WorkException;
import com.dasi.types.result.PageResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class XhsPublishService implements IXhsPublishService {

    @Resource
    private IXhsPublishTaskRepository taskRepository;

    @Resource
    private IXhsPublishAttemptRepository attemptRepository;

    @Resource
    private IXhsPublishReviewRepository reviewRepository;

    @Resource
    private IXhsPublishContentAssetRepository contentAssetRepository;

    @Resource
    private IXhsPublishKnowledgeDocRepository knowledgeDocRepository;

    @Resource
    private IXhsPublishTemplateRepository templateRepository;

    @Resource
    private IXhsPublishSnapshotRepository snapshotRepository;

    @Resource
    private IXhsAssetStoragePort assetStoragePort;

    @Resource
    private IXhsVectorStorePort vectorStorePort;

    @Resource
    private XhsPublishExecuteStrategyFactory strategyFactory;

    @Resource
    private XhsPublishBindingSupport bindingSupport;

    @Resource
    private XhsPublishIdSupport idSupport;

    @Resource
    private IPublishValidationDomainService validationDomainService;

    @Resource
    private IPublishRetryDomainService retryDomainService;

    @Resource
    private IPublishTaskStateMachine taskStateMachine;

    @Resource
    private UserContext userContext;

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Resource
    private XhsPublishProperties xhsPublishProperties;

    @Override
    public String createTask(CreateXhsPublishTaskDTO dto) {
        Long userId = requireUserId();
        validateModeAndType(dto.getPublishMode(), dto.getPublishType());
        validationDomainService.validateTaskRequest(dto.getPublishType(), dto.getRequestJson());

        XhsPublishAccountBindingEntity binding = bindingSupport.resolveBinding(userId, dto.getBindingId());
        String taskId = idSupport.nextTaskId();
        XhsPublishTaskEntity task = XhsPublishTaskEntity.builder()
                .taskId(taskId)
                .userId(userId)
                .taskName(dto.getTaskName())
                .publishMode(dto.getPublishMode())
                .publishType(dto.getPublishType())
                .taskStatus(PublishTaskStatusVO.running.name())
                .currentStage(PublishStageVO.planning.name())
                .bindingId(binding.getBindingId())
                .templateId(dto.getTemplateId())
                .scheduledPublishAt(dto.getScheduledPublishAt())
                .requestJson(dto.getRequestJson())
                .latestContextJson(dto.getRequestJson())
                .retryPolicyJson(dto.getRetryPolicyJson())
                .build();
        taskRepository.insert(task);
        XhsPublishTaskEntity persistedTask = taskRepository.queryByTaskId(taskId);

        XhsPublishAttemptEntity attempt = newAttempt(persistedTask, 1, "create", dto.getRequestJson());
        attemptRepository.insert(attempt);
        XhsPublishAttemptEntity persistedAttempt = attemptRepository.queryByAttemptId(attempt.getAttemptId());

        executeStrategy(persistedTask, persistedAttempt, binding, contentAssetRepository.listByTaskId(taskId));
        return taskId;
    }

    @Override
    public PageResult<XhsPublishTaskPageVO> pageTask(PageXhsPublishTaskDTO dto) {
        Long userId = requireUserId();
        int pageNum = Math.max(dto.getPageNum(), 1);
        int pageSize = Math.max(dto.getPageSize(), 1);
        int offset = (pageNum - 1) * pageSize;
        List<XhsPublishTaskPageVO> list = taskRepository.page(userId, dto.getKeyword(), dto.getTaskStatus(), dto.getCurrentStage(), offset, pageSize).stream()
                .map(this::toTaskPageVO)
                .toList();
        Integer total = taskRepository.count(userId, dto.getKeyword(), dto.getTaskStatus(), dto.getCurrentStage());
        int pageSum = (total + pageSize - 1) / pageSize;
        return PageResult.<XhsPublishTaskPageVO>builder()
                .list(list)
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .pageSum(pageSum)
                .build();
    }

    @Override
    public XhsPublishTaskDetailVO detailTask(String taskId) {
        XhsPublishTaskEntity task = queryOwnedTask(taskId);
        List<XhsPublishAttemptVO> attemptList = attemptRepository.listByTaskId(taskId).stream().map(this::toAttemptVO).toList();
        List<XhsPublishReviewVO> reviewList = reviewRepository.listByTaskId(taskId).stream().map(this::toReviewVO).toList();
        List<XhsPublishAssetVO> assetList = contentAssetRepository.listByTaskId(taskId).stream().map(this::toAssetVO).toList();
        return XhsPublishTaskDetailVO.builder()
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
                .attemptList(attemptList)
                .reviewList(reviewList)
                .assetList(assetList)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewTask(ReviewXhsPublishTaskDTO dto) {
        XhsPublishTaskEntity task = queryOwnedTask(dto.getTaskId());
        XhsPublishReviewEntity entity = XhsPublishReviewEntity.builder()
                .reviewId(idSupport.nextReviewId())
                .taskId(task.getTaskId())
                .attemptId(dto.getAttemptId())
                .reviewStage(dto.getReviewStage())
                .reviewMode(dto.getReviewMode())
                .reviewStatus(dto.getReviewStatus())
                .reviewerId(requireUserId())
                .reviewComment(dto.getReviewComment())
                .reviewPayloadJson(dto.getReviewPayloadJson())
                .decisionJson(dto.getDecisionJson())
                .decidedTime(LocalDateTime.now())
                .build();
        reviewRepository.insert(entity);
    }

    @Override
    public void retryTask(RetryXhsPublishTaskDTO dto) {
        XhsPublishTaskEntity task = queryOwnedTask(dto.getTaskId());
        List<XhsPublishAttemptEntity> attemptList = attemptRepository.listByTaskId(task.getTaskId());
        XhsPublishAttemptEntity latestAttempt = attemptList.isEmpty() ? null : attemptList.get(0);
        if (latestAttempt != null && !retryDomainService.canRetry(task.getRetryPolicyJson(), latestAttempt.getAttemptNo(), latestAttempt.getErrorCode())) {
            throw new WorkException("当前任务不满足重试条件");
        }

        if (StringUtils.hasText(dto.getOverrideContextJson())) {
            validationDomainService.validateTaskRequest(task.getPublishType(), dto.getOverrideContextJson());
            task.setLatestContextJson(dto.getOverrideContextJson());
            taskRepository.update(task);
        }

        XhsPublishAccountBindingEntity binding = bindingSupport.resolveBinding(task.getUserId(), task.getBindingId());
        int attemptNo = attemptList.size() + 1;
        String contextJson = StringUtils.hasText(task.getLatestContextJson()) ? task.getLatestContextJson() : task.getRequestJson();
        XhsPublishAttemptEntity attempt = newAttempt(task, attemptNo, dto.getTriggerType(), contextJson);
        attemptRepository.insert(attempt);
        XhsPublishAttemptEntity persistedAttempt = attemptRepository.queryByAttemptId(attempt.getAttemptId());

        task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "retry_requested"));
        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "start_execute"));
        taskRepository.update(task);

        executeStrategy(task, persistedAttempt, binding, contentAssetRepository.listByTaskId(task.getTaskId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTemplate(SaveXhsPublishTemplateDTO dto) {
        Long userId = requireUserId();
        if (StringUtils.hasText(dto.getTemplateId())) {
            XhsPublishTemplateEntity existing = templateRepository.queryByTemplateId(dto.getTemplateId());
            if (existing == null || !userId.equals(existing.getUserId())) {
                throw new WorkException("模板不存在");
            }
            existing.setTemplateName(dto.getTemplateName());
            existing.setPublishMode(dto.getPublishMode());
            existing.setTemplateConfigJson(dto.getTemplateConfigJson());
            existing.setTemplateStatus(dto.getTemplateStatus());
            existing.setIsDefault(dto.getIsDefault());
            templateRepository.update(existing);
            return;
        }

        XhsPublishTemplateEntity entity = XhsPublishTemplateEntity.builder()
                .templateId(idSupport.nextTemplateId())
                .userId(userId)
                .templateName(dto.getTemplateName())
                .publishMode(dto.getPublishMode())
                .templateConfigJson(dto.getTemplateConfigJson())
                .templateStatus(dto.getTemplateStatus())
                .isDefault(dto.getIsDefault())
                .build();
        templateRepository.insert(entity);
    }

    @Override
    public PageResult<XhsPublishTemplateVO> pageTemplate(PageXhsPublishTemplateDTO dto) {
        Long userId = requireUserId();
        int pageNum = Math.max(dto.getPageNum(), 1);
        int pageSize = Math.max(dto.getPageSize(), 1);
        int offset = (pageNum - 1) * pageSize;
        List<XhsPublishTemplateVO> list = templateRepository.page(userId, dto.getKeyword(), offset, pageSize).stream()
                .map(this::toTemplateVO)
                .toList();
        Integer total = templateRepository.count(userId, dto.getKeyword());
        int pageSum = (total + pageSize - 1) / pageSize;
        return PageResult.<XhsPublishTemplateVO>builder()
                .list(list)
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .pageSum(pageSum)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<XhsPublishAssetVO> uploadMaterial(UploadXhsPublishMaterialDTO dto, List<MultipartFile> fileList) {
        Long userId = requireUserId();
        queryOwnedTask(dto.getTaskId());

        List<XhsPublishAssetVO> result = new ArrayList<>();
        int sortNo = dto.getSortNo() == null ? 1 : dto.getSortNo();

        if (fileList != null && !fileList.isEmpty()) {
            for (MultipartFile file : fileList) {
                String storageRef = assetStoragePort.save(file, dto.getTaskId());
                String assetId = idSupport.nextAssetId();
                XhsPublishContentAssetEntity entity = XhsPublishContentAssetEntity.builder()
                        .assetId(assetId)
                        .taskId(dto.getTaskId())
                        .attemptId(dto.getAttemptId())
                        .userId(userId)
                        .assetType(dto.getAssetType())
                        .sourceType(dto.getSourceType())
                        .storageType("local")
                        .storageRef(storageRef)
                        .accessUrl(buildAssetAccessUrl(assetId))
                        .sortNo(sortNo++)
                        .assetStatus("ready")
                        .expireTime(LocalDateTime.now().plusHours(defaultHours(xhsPublishProperties.getAssetRetentionHours())))
                        .build();
                contentAssetRepository.insert(entity);
                result.add(toAssetVO(contentAssetRepository.queryByAssetId(assetId)));
            }
            return result;
        }

        if (!StringUtils.hasText(dto.getOriginUrl())) {
            throw new WorkException("请上传文件或提供 originUrl");
        }
        String assetId = idSupport.nextAssetId();
        XhsPublishContentAssetEntity entity = XhsPublishContentAssetEntity.builder()
                .assetId(assetId)
                .taskId(dto.getTaskId())
                .attemptId(dto.getAttemptId())
                .userId(userId)
                .assetType(dto.getAssetType())
                .sourceType(dto.getSourceType())
                .originUrl(dto.getOriginUrl())
                .storageType("url")
                .storageRef(dto.getOriginUrl())
                .accessUrl(dto.getOriginUrl())
                .sortNo(sortNo)
                .assetStatus("ready")
                .expireTime(LocalDateTime.now().plusHours(defaultHours(xhsPublishProperties.getAssetRetentionHours())))
                .build();
        contentAssetRepository.insert(entity);
        result.add(toAssetVO(contentAssetRepository.queryByAssetId(assetId)));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadKnowledge(UploadXhsPublishKnowledgeDTO dto, List<MultipartFile> fileList) {
        Long userId = requireUserId();
        if ("shared".equalsIgnoreCase(dto.getKnowledgeScope()) && !"admin".equalsIgnoreCase(userContext.getUserRole())) {
            throw new WorkException("共享知识库仅管理员可写");
        }

        String knowledgeId = idSupport.nextKnowledgeId();
        XhsPublishKnowledgeDocEntity entity = XhsPublishKnowledgeDocEntity.builder()
                .knowledgeId(knowledgeId)
                .userId(userId)
                .knowledgeScope(dto.getKnowledgeScope())
                .knowledgeType(dto.getKnowledgeType())
                .title(dto.getTitle())
                .ragTag(dto.getRagTag())
                .sourceType(dto.getSourceType())
                .sourceRef(dto.getSourceRef())
                .summary(dto.getSummary())
                .docStatus("active")
                .vectorStatus("pending")
                .build();
        knowledgeDocRepository.insert(entity);
        entity = knowledgeDocRepository.queryByKnowledgeId(knowledgeId);

        try {
            int chunks = 0;
            if (fileList != null && !fileList.isEmpty()) {
                for (MultipartFile file : fileList) {
                    TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
                    List<Document> documents = tokenTextSplitter.apply(reader.get());
                    for (Document document : documents) {
                        if (document == null || !StringUtils.hasText(document.getText())) {
                            continue;
                        }
                        vectorStorePort.upsert(document.getText(), buildKnowledgeMetadata(dto, knowledgeId, userId));
                        chunks++;
                    }
                }
            } else if (StringUtils.hasText(dto.getSummary()) || StringUtils.hasText(dto.getSourceRef())) {
                String content = StringUtils.hasText(dto.getSummary()) ? dto.getSummary() : dto.getSourceRef();
                vectorStorePort.upsert(content, buildKnowledgeMetadata(dto, knowledgeId, userId));
                chunks++;
            } else {
                throw new WorkException("知识内容不能为空，请上传文件或填写摘要/来源文本");
            }

            entity.setVectorStatus(chunks > 0 ? "success" : "failed");
            knowledgeDocRepository.update(entity);
        } catch (Exception e) {
            entity.setVectorStatus("failed");
            knowledgeDocRepository.update(entity);
            throw e;
        }
    }

    @Override
    public org.springframework.core.io.Resource accessMaterial(String assetId) {
        XhsPublishContentAssetEntity asset = contentAssetRepository.queryByAssetId(assetId);
        if (asset == null || !Objects.equals(asset.getUserId(), requireUserId())) {
            throw new WorkException("素材不存在");
        }
        if (!"local".equalsIgnoreCase(asset.getStorageType())) {
            throw new WorkException("当前素材为远程地址，请直接使用 accessUrl");
        }
        return assetStoragePort.loadAsResource(asset.getStorageRef());
    }

    @Override
    public void cleanupExpiredAssets() {
        List<XhsPublishContentAssetEntity> expiredList = contentAssetRepository.listExpired(LocalDateTime.now());
        for (XhsPublishContentAssetEntity asset : expiredList) {
            if (asset == null || "deleted".equalsIgnoreCase(asset.getAssetStatus())) {
                continue;
            }
            if ("local".equalsIgnoreCase(asset.getStorageType())) {
                assetStoragePort.delete(asset.getStorageRef());
            }
            asset.setAssetStatus("deleted");
            contentAssetRepository.update(asset);
        }
    }

    @Override
    public void cleanupExpiredSnapshots() {
        List<XhsPublishSnapshotEntity> expiredList = snapshotRepository.listExpired(LocalDateTime.now());
        for (XhsPublishSnapshotEntity snapshot : expiredList) {
            if (snapshot == null || "deleted".equalsIgnoreCase(snapshot.getCleanupStatus())) {
                continue;
            }
            // 失败快照保留到过期，再由任务统一回收。
            String snapshotPath = snapshot.getSnapshotPath();
            if (snapshotPath != null && !snapshotPath.isBlank()) {
                try {
                    java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(snapshotPath));
                } catch (Exception e) {
                    log.warn("【小红书发布】删除过期快照文件失败：snapshotId={}, path={}", snapshot.getSnapshotId(), snapshotPath, e);
                }
            }
            snapshot.setCleanupStatus("deleted");
            snapshot.setDeletedTime(LocalDateTime.now());
            snapshotRepository.update(snapshot);
        }
    }

    private void executeStrategy(XhsPublishTaskEntity task,
                                 XhsPublishAttemptEntity attempt,
                                 XhsPublishAccountBindingEntity binding,
                                 List<XhsPublishContentAssetEntity> assetList) {
        IXhsPublishExecuteStrategy strategy = strategyFactory.getStrategy(task.getPublishMode(), task.getPublishType());
        if (strategy == null) {
            throw new WorkException("当前发布模式/类型尚未接入执行策略");
        }

        String sourceJson = StringUtils.hasText(task.getLatestContextJson()) ? task.getLatestContextJson() : task.getRequestJson();
        JSONObject sourceContext = JSON.parseObject(sourceJson);
        strategy.execute(XhsPublishExecutionContext.builder()
                .task(task)
                .attempt(attempt)
                .binding(binding)
                .assetList(assetList)
                .sourceContext(sourceContext)
                .build());
    }

    private XhsPublishTaskEntity queryOwnedTask(String taskId) {
        XhsPublishTaskEntity task = taskRepository.queryByTaskId(taskId);
        Long userId = requireUserId();
        if (task == null || !Objects.equals(task.getUserId(), userId)) {
            throw new WorkException("发布任务不存在");
        }
        return task;
    }

    private XhsPublishAttemptEntity newAttempt(XhsPublishTaskEntity task, int attemptNo, String triggerType, String contextJson) {
        return XhsPublishAttemptEntity.builder()
                .attemptId(idSupport.nextAttemptId())
                .taskId(task.getTaskId())
                .attemptNo(attemptNo)
                .triggerType(triggerType)
                .attemptStatus("running")
                .stage(PublishStageVO.planning.name())
                .contextJson(contextJson)
                .retryable(1)
                .build();
    }

    private Map<String, Object> buildKnowledgeMetadata(UploadXhsPublishKnowledgeDTO dto, String knowledgeId, Long userId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("bizDomain", "xhs_publish");
        metadata.put("knowledgeId", knowledgeId);
        metadata.put("knowledge", dto.getRagTag());
        metadata.put("knowledgeScope", dto.getKnowledgeScope());
        metadata.put("knowledgeType", dto.getKnowledgeType());
        metadata.put("userId", String.valueOf(userId));
        metadata.put("sourceType", dto.getSourceType());
        metadata.put("status", "active");
        metadata.put("docTitle", dto.getTitle());
        return metadata;
    }

    private void validateModeAndType(String publishMode, String publishType) {
        try {
            PublishModeVO.valueOf(publishMode);
        } catch (Exception e) {
            throw new WorkException("当前仅支持 A/B 两种发布模式");
        }
        try {
            PublishTypeVO.valueOf(publishType);
        } catch (Exception e) {
            throw new WorkException("当前仅支持 image/video 发布类型");
        }
    }

    private Long requireUserId() {
        Long userId = userContext.getUserId();
        if (userId == null) {
            throw new WorkException("当前用户未登录");
        }
        return userId;
    }

    private int defaultHours(Integer hours) {
        return hours == null ? 24 : hours;
    }

    private String buildAssetAccessUrl(String assetId) {
        return xhsPublishProperties.getAssetAccessBaseUrl() + "?assetId=" + assetId;
    }

    private XhsPublishTaskPageVO toTaskPageVO(XhsPublishTaskEntity entity) {
        return XhsPublishTaskPageVO.builder()
                .taskId(entity.getTaskId())
                .taskName(entity.getTaskName())
                .publishMode(entity.getPublishMode())
                .publishType(entity.getPublishType())
                .taskStatus(entity.getTaskStatus())
                .currentStage(entity.getCurrentStage())
                .scheduledPublishAt(entity.getScheduledPublishAt())
                .updateTime(entity.getUpdateTime())
                .build();
    }

    private XhsPublishAttemptVO toAttemptVO(XhsPublishAttemptEntity entity) {
        return XhsPublishAttemptVO.builder()
                .attemptId(entity.getAttemptId())
                .attemptNo(entity.getAttemptNo())
                .attemptStatus(entity.getAttemptStatus())
                .stage(entity.getStage())
                .errorCode(entity.getErrorCode())
                .errorMessage(entity.getErrorMessage())
                .costAmount(entity.getCostAmount())
                .startTime(entity.getStartTime())
                .endTime(entity.getEndTime())
                .build();
    }

    private XhsPublishReviewVO toReviewVO(XhsPublishReviewEntity entity) {
        return XhsPublishReviewVO.builder()
                .reviewId(entity.getReviewId())
                .reviewStage(entity.getReviewStage())
                .reviewMode(entity.getReviewMode())
                .reviewStatus(entity.getReviewStatus())
                .reviewComment(entity.getReviewComment())
                .decidedTime(entity.getDecidedTime())
                .build();
    }

    private XhsPublishAssetVO toAssetVO(XhsPublishContentAssetEntity entity) {
        return XhsPublishAssetVO.builder()
                .assetId(entity.getAssetId())
                .assetType(entity.getAssetType())
                .sourceType(entity.getSourceType())
                .storageType(entity.getStorageType())
                .accessUrl(entity.getAccessUrl())
                .sortNo(entity.getSortNo())
                .assetStatus(entity.getAssetStatus())
                .expireTime(entity.getExpireTime())
                .build();
    }

    private XhsPublishTemplateVO toTemplateVO(XhsPublishTemplateEntity entity) {
        return XhsPublishTemplateVO.builder()
                .templateId(entity.getTemplateId())
                .templateName(entity.getTemplateName())
                .publishMode(entity.getPublishMode())
                .templateStatus(entity.getTemplateStatus())
                .isDefault(entity.getIsDefault())
                .updateTime(entity.getUpdateTime())
                .build();
    }

}
