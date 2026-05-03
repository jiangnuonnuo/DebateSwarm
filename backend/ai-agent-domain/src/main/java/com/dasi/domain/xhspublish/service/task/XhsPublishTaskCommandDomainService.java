package com.dasi.domain.xhspublish.service.task;

import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishTemplateRepository;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAccountBindingEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptStatsEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishCreateTaskCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishReviewEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishReviewCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishRetryCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishSubmitTaskCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTemplateEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishUpdateContextCommandEntity;
import com.dasi.domain.xhspublish.model.valobj.PublishAttemptStatusVO;
import com.dasi.domain.xhspublish.model.valobj.PublishModeVO;
import com.dasi.domain.xhspublish.model.valobj.PublishStageVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTaskStatusVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTypeVO;
import com.dasi.domain.xhspublish.model.valobj.ReviewStageVO;
import com.dasi.domain.xhspublish.model.valobj.ReviewStatusVO;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.domain.IPublishRetryDomainService;
import com.dasi.domain.xhspublish.service.domain.IPublishTaskStateMachine;
import com.dasi.domain.xhspublish.service.domain.IPublishValidationDomainService;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionContext;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;
import com.dasi.domain.xhspublish.service.support.XhsPublishBindingSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishExecutionSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import com.dasi.domain.xhspublish.service.support.RetryDecisionMessageSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskAccessSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskLockSupport;
import com.dasi.domain.xhspublish.service.strategy.IXhsPublishExecuteStrategy;
import com.dasi.domain.xhspublish.service.strategy.XhsPublishExecuteStrategyFactory;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class XhsPublishTaskCommandDomainService {

    @Resource
    private IXhsPublishRepository publishRepository;

    @Resource
    private XhsPublishBindingSupport bindingSupport;

    @Resource
    private IXhsPublishTemplateRepository templateRepository;

    @Resource
    private XhsPublishIdSupport idSupport;

    @Resource
    private IPublishValidationDomainService validationDomainService;

    @Resource
    private IPublishRetryDomainService retryDomainService;

    @Resource
    private IPublishTaskStateMachine taskStateMachine;

    @Resource
    private XhsPublishTaskAccessSupport taskAccessSupport;

    @Resource
    private XhsPublishExecutionSupport executionSupport;

    @Resource
    private XhsPublishTaskLockSupport taskLockSupport;

    @Resource
    private XhsPublishExecuteStrategyFactory executeStrategyFactory;

    public String createTask(XhsPublishCreateTaskCommandEntity dto) {
        // 步骤 1：校验当前用户、发布模式/类型和 requestJson 契约，确保草稿从一开始就是合法状态。
        Long userId = taskAccessSupport.requireUserId();
        taskAccessSupport.validateModeAndType(dto.getPublishMode(), dto.getPublishType());
        String requestJson = resolveTaskRequestJson(userId, dto);
        validationDomainService.validateTaskRequest(dto.getPublishType(), requestJson);

        // 步骤 2：解析可用发布账号；没有显式 binding 时，按默认账号兜底初始化。
        XhsPublishAccountBindingEntity binding = bindingSupport.resolveBinding(userId, dto.getBindingId());

        // 步骤 3：创建 draft/planning 草稿，requestJson 与 latestContextJson 初始保持一致。
        String taskId = idSupport.nextTaskId();
        XhsPublishTaskEntity task = XhsPublishTaskEntity.builder()
                .taskId(taskId)
                .userId(userId)
                .taskName(dto.getTaskName())
                .publishMode(dto.getPublishMode())
                .publishType(dto.getPublishType())
                .taskStatus(PublishTaskStatusVO.draft.name())
                .currentStage(PublishStageVO.planning.name())
                .bindingId(binding.getBindingId())
                .templateId(dto.getTemplateId())
                .scheduledPublishAt(dto.getScheduledPublishAt())
                .requestJson(requestJson)
                .latestContextJson(requestJson)
                .retryPolicyJson(dto.getRetryPolicyJson())
                .build();
        publishRepository.saveTask(task);
        return taskId;
    }

    public void updateTaskContext(XhsPublishUpdateContextCommandEntity dto) {
        // 只允许在 planning/审核等待等可编辑阶段更新上下文，执行中和终态任务禁止覆盖。
        XhsPublishTaskEntity task = taskAccessSupport.queryOwnedTask(dto.getTaskId());
        if (PublishStageVO.publishing.name().equals(task.getCurrentStage())
                || PublishStageVO.completed.name().equals(task.getCurrentStage())
                || PublishStageVO.cancelled_terminal.name().equals(task.getCurrentStage())) {
            throw new WorkException("当前任务状态不允许更新上下文");
        }
        validationDomainService.validateTaskRequest(task.getPublishType(), dto.getLatestContextJson());
        task.setLatestContextJson(dto.getLatestContextJson());
        publishRepository.saveTask(task);
    }

    public String submitTask(XhsPublishSubmitTaskCommandEntity dto) {
        return taskLockSupport.withTaskLock(dto.getTaskId(), () -> doSubmitTask(dto));
    }

    @Transactional(rollbackFor = Exception.class)
    public void reviewTask(XhsPublishReviewCommandEntity dto) {
        taskLockSupport.withTaskLock(dto.getTaskId(), () -> {
            XhsPublishTaskEntity task = taskAccessSupport.queryOwnedTask(dto.getTaskId());
            String attemptId = resolveAttemptId(task.getTaskId(), dto.getAttemptId());
            XhsPublishReviewEntity entity = XhsPublishReviewEntity.builder()
                    .reviewId(idSupport.nextReviewId())
                    .taskId(task.getTaskId())
                    .attemptId(attemptId)
                    .reviewStage(dto.getReviewStage())
                    .reviewMode(dto.getReviewMode())
                    .reviewStatus(dto.getReviewStatus())
                    .reviewerId(taskAccessSupport.requireUserId())
                    .reviewComment(dto.getReviewComment())
                    .reviewPayloadJson(dto.getReviewPayloadJson())
                    .decisionJson(dto.getDecisionJson())
                    .decidedTime(LocalDateTime.now())
                    .build();
            publishRepository.saveReview(entity);

            // A 模式审核必须驱动 task/attempt 状态，避免“只留审计记录不影响执行流”的假通过。
            if (PublishModeVO.A.name().equals(task.getPublishMode())) {
                applyReviewDecision(task, entity);
            }
            return null;
        });
    }

    public void retryTask(XhsPublishRetryCommandEntity dto) {
        taskLockSupport.withTaskLock(dto.getTaskId(), () -> doRetryTask(dto));
    }

    private String doSubmitTask(XhsPublishSubmitTaskCommandEntity dto) {
        XhsPublishTaskEntity task = taskAccessSupport.queryOwnedTask(dto.getTaskId());
        if (isBImageTask(task)) {
            return submitByBTree(task, dto);
        }

        // 步骤 1：submit 入口负责做并发防重 + 可选上下文覆盖，不让 controller 直接操作任务状态。
        if (StringUtils.hasText(dto.getOverrideContextJson())) {
            validationDomainService.validateTaskRequest(task.getPublishType(), dto.getOverrideContextJson());
            task.setLatestContextJson(dto.getOverrideContextJson());
        }

        // 步骤 2：解析发布账号并检查是否已有活跃 attempt，避免同任务并发重复提交。
        XhsPublishAccountBindingEntity binding = bindingSupport.resolveBinding(task.getUserId(),
                StringUtils.hasText(dto.getBindingId()) ? dto.getBindingId() : task.getBindingId());
        List<XhsPublishAttemptEntity> attemptList = publishRepository.listAttemptByTaskId(task.getTaskId());
        ensureNoActiveAttempt(attemptList);

        // 步骤 3：先推进 task 的状态机，再创建本次 attempt，保证 task/attempt 三轴一致。
        task.setBindingId(binding.getBindingId());
        task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "submit_requested"));
        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "submit_requested"));
        publishRepository.saveTask(task);

        int attemptNo = attemptList.size() + 1;
        String contextJson = StringUtils.hasText(task.getLatestContextJson()) ? task.getLatestContextJson() : task.getRequestJson();
        XhsPublishAttemptEntity attempt = newAttempt(task, attemptNo, safeTriggerType(dto.getTriggerType(), "submit"), contextJson);
        publishRepository.saveAttempt(attempt);
        XhsPublishAttemptEntity persistedAttempt = publishRepository.queryAttemptByAttemptId(attempt.getAttemptId());

        // 步骤 4：A 模式先阻断到审核；B 模式直接进入执行树。
        if (PublishModeVO.A.name().equals(task.getPublishMode())) {
            enterCopyReviewPending(task, persistedAttempt);
            return persistedAttempt.getAttemptId();
        }

        // 步骤 5：统一把 task/attempt/binding/asset 交给执行支持类，后续走策略 + 执行树。
        executionSupport.execute(task, persistedAttempt, binding, publishRepository.listAssetByTaskId(task.getTaskId()));
        return persistedAttempt.getAttemptId();
    }

    private void doRetryTask(XhsPublishRetryCommandEntity dto) {
        XhsPublishTaskEntity task = taskAccessSupport.queryOwnedTask(dto.getTaskId());
        if (isBImageTask(task)) {
            retryByBTree(task, dto);
            return;
        }

        // 步骤 1：retry 只消费“最新 attempt + 聚合统计 + 责任链决策”，避免散落 if-else 判断。
        XhsPublishAttemptEntity latestAttempt = publishRepository.queryLatestAttemptByTaskId(task.getTaskId());
        if (latestAttempt == null) {
            throw new WorkException("当前任务暂无可重试的执行记录");
        }
        XhsPublishAttemptStatsEntity attemptStats = publishRepository.queryAttemptStatsByTaskId(task.getTaskId());

        RetryDecisionResult retryDecision = retryDomainService.evaluate(RetryDecisionContext.builder()
                .retryPolicyJson(task.getRetryPolicyJson())
                .latestAttemptNo(latestAttempt.getAttemptNo())
                .latestErrorCode(latestAttempt.getErrorCode())
                .totalDurationMs(attemptStats == null ? 0L : attemptStats.getTotalDurationMs())
                .totalCostAmount(attemptStats == null ? BigDecimal.ZERO : attemptStats.getTotalCostAmount())
                .activeAttempt(isActiveAttempt(latestAttempt))
                .build());
        if (!retryDecision.isAllowed()) {
            throw new WorkException(RetryDecisionMessageSupport.toUserMessage(retryDecision));
        }

        // 步骤 2：如有 overrideContextJson，先替换 latestContextJson，再创建新的 retry attempt。
        if (StringUtils.hasText(dto.getOverrideContextJson())) {
            validationDomainService.validateTaskRequest(task.getPublishType(), dto.getOverrideContextJson());
            task.setLatestContextJson(dto.getOverrideContextJson());
            publishRepository.saveTask(task);
        }

        XhsPublishAccountBindingEntity binding = bindingSupport.resolveBinding(task.getUserId(), task.getBindingId());
        int attemptNo = nextAttemptNo(attemptStats, latestAttempt);
        String contextJson = StringUtils.hasText(task.getLatestContextJson()) ? task.getLatestContextJson() : task.getRequestJson();
        XhsPublishAttemptEntity attempt = newAttempt(task, attemptNo, safeTriggerType(dto.getTriggerType(), "retry"), contextJson);
        publishRepository.saveAttempt(attempt);
        XhsPublishAttemptEntity persistedAttempt = publishRepository.queryAttemptByAttemptId(attempt.getAttemptId());

        // 步骤 3：retry_requested -> start_execute 后，再复用同一条执行树。
        task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "retry_requested"));
        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "start_execute"));
        publishRepository.saveTask(task);

        executionSupport.execute(task, persistedAttempt, binding, publishRepository.listAssetByTaskId(task.getTaskId()));
    }

    private XhsPublishAttemptEntity newAttempt(XhsPublishTaskEntity task, int attemptNo, String triggerType, String contextJson) {
        return XhsPublishAttemptEntity.builder()
                .attemptId(idSupport.nextAttemptId())
                .taskId(task.getTaskId())
                .attemptNo(attemptNo)
                .triggerType(triggerType)
                .attemptStatus(PublishAttemptStatusVO.created.name())
                .stage(PublishStageVO.planning.name())
                .contextJson(contextJson)
                .retryable(1)
                // 新建 attempt 还未真正执行时，成本从 0 起步，避免 NOT NULL 字段在落库前为空。
                .costAmount(BigDecimal.ZERO)
                // 新建 attempt 还未产生远端耗时，持续时间默认记为 0，后续由生命周期节点改写真实值。
                .durationMs(0L)
                .build();
    }

    private void ensureNoActiveAttempt(List<XhsPublishAttemptEntity> attemptList) {
        if (attemptList == null || attemptList.isEmpty()) {
            return;
        }
        if (isActiveAttempt(attemptList.get(0))) {
            throw new WorkException("任务正在执行中，请勿重复提交");
        }
    }

    private boolean isActiveAttempt(XhsPublishAttemptEntity latestAttempt) {
        if (latestAttempt == null || !StringUtils.hasText(latestAttempt.getAttemptStatus())) {
            return false;
        }
        String status = latestAttempt.getAttemptStatus();
        return PublishAttemptStatusVO.created.name().equals(status)
                || PublishAttemptStatusVO.running.name().equals(status)
                || PublishAttemptStatusVO.waiting_review.name().equals(status)
                || PublishAttemptStatusVO.accepted.name().equals(status);
    }

    private int nextAttemptNo(XhsPublishAttemptStatsEntity attemptStats, XhsPublishAttemptEntity latestAttempt) {
        Integer maxAttemptNo = attemptStats == null ? null : attemptStats.getMaxAttemptNo();
        if (maxAttemptNo == null || maxAttemptNo <= 0) {
            if (latestAttempt == null || latestAttempt.getAttemptNo() == null) {
                return 1;
            }
            return latestAttempt.getAttemptNo() + 1;
        }
        return maxAttemptNo + 1;
    }

    private String safeTriggerType(String triggerType, String defaultValue) {
        return StringUtils.hasText(triggerType) ? triggerType : defaultValue;
    }

    private String resolveAttemptId(String taskId, String attemptId) {
        if (StringUtils.hasText(attemptId)) {
            return attemptId;
        }
        XhsPublishAttemptEntity latestAttempt = publishRepository.queryLatestAttemptByTaskId(taskId);
        if (latestAttempt == null || !StringUtils.hasText(latestAttempt.getAttemptId())) {
            throw new WorkException("审核失败：未找到对应执行记录");
        }
        return latestAttempt.getAttemptId();
    }

    private void applyReviewDecision(XhsPublishTaskEntity task, XhsPublishReviewEntity reviewEntity) {
        if (reviewEntity == null || !StringUtils.hasText(reviewEntity.getAttemptId())) {
            return;
        }
        XhsPublishAttemptEntity attempt = publishRepository.queryAttemptByAttemptId(reviewEntity.getAttemptId());
        if (attempt == null) {
            throw new WorkException("审核失败：执行记录不存在");
        }

        String reviewStatus = normalizeReviewStatus(reviewEntity.getReviewStatus());
        String reviewStage = normalizeReviewStage(reviewEntity.getReviewStage());
        if (ReviewStatusVO.pending.name().equals(reviewStatus)) {
            return;
        }

        if (ReviewStatusVO.approved.name().equals(reviewStatus) || ReviewStatusVO.skipped.name().equals(reviewStatus)) {
            String resumeEvent = resolveApproveEvent(reviewStage);
            task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), resumeEvent));
            task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), resumeEvent));
            publishRepository.saveTask(task);

            attempt.setAttemptStatus(taskStateMachine.nextAttemptStatus(attempt.getAttemptStatus(), resumeEvent));
            attempt.setStage(task.getCurrentStage());
            publishRepository.saveAttempt(attempt);
            if (ReviewStageVO.copy_review.name().equals(reviewStage)) {
                XhsPublishAccountBindingEntity binding = bindingSupport.resolveBinding(task.getUserId(), task.getBindingId());
                executionSupport.execute(task, attempt, binding, publishRepository.listAssetByTaskId(task.getTaskId()));
            }
            return;
        }

        if (ReviewStatusVO.rejected.name().equals(reviewStatus)) {
            task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "validation_rejected"));
            task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "validation_rejected"));
            publishRepository.saveTask(task);

            attempt.setAttemptStatus(taskStateMachine.nextAttemptStatus(attempt.getAttemptStatus(), "validation_rejected"));
            attempt.setStage(task.getCurrentStage());
            attempt.setRetryable(1);
            attempt.setErrorCode("REVIEW_REJECTED");
            attempt.setErrorMessage(StringUtils.hasText(reviewEntity.getReviewComment()) ? reviewEntity.getReviewComment() : "审核拒绝");
            publishRepository.saveAttempt(attempt);
        }
    }

    private String normalizeReviewStatus(String reviewStatus) {
        try {
            return ReviewStatusVO.valueOf(reviewStatus).name();
        } catch (Exception e) {
            throw new WorkException("审核状态不支持");
        }
    }

    private String normalizeReviewStage(String reviewStage) {
        try {
            return ReviewStageVO.valueOf(reviewStage).name();
        } catch (Exception e) {
            throw new WorkException("审核阶段不支持");
        }
    }

    private String resolveApproveEvent(String reviewStage) {
        if (ReviewStageVO.copy_review.name().equals(reviewStage)) {
            return "copy_review_approved";
        }
        if (ReviewStageVO.pre_publish_review.name().equals(reviewStage)) {
            return "pre_publish_review_approved";
        }
        return "attempt_resumed";
    }

    private String resolveTaskRequestJson(Long userId, XhsPublishCreateTaskCommandEntity dto) {
        if (!StringUtils.hasText(dto.getTemplateId())) {
            return dto.getRequestJson();
        }

        XhsPublishTemplateEntity template = templateRepository.queryByTemplateId(dto.getTemplateId());
        if (template == null || !userId.equals(template.getUserId())) {
            throw new WorkException("模板不存在");
        }
        if (template.getTemplateStatus() != null && template.getTemplateStatus() != 1) {
            throw new WorkException("模板已停用");
        }
        if (!isEmptyJsonObject(dto.getRequestJson())) {
            return dto.getRequestJson();
        }
        if (!StringUtils.hasText(template.getTemplateConfigJson())) {
            throw new WorkException("模板内容为空");
        }
        return template.getTemplateConfigJson();
    }

    private boolean isEmptyJsonObject(String json) {
        return "{}".equals(StringUtils.trimWhitespace(json));
    }

    private boolean isBImageTask(XhsPublishTaskEntity task) {
        return task != null
                && PublishModeVO.B.name().equals(task.getPublishMode())
                && PublishTypeVO.image.name().equalsIgnoreCase(task.getPublishType());
    }

    private String submitByBTree(XhsPublishTaskEntity task, XhsPublishSubmitTaskCommandEntity dto) {
        IXhsPublishExecuteStrategy strategy = requireExecuteStrategy(task);
        XhsPublishExecutionContext executionContext = XhsPublishExecutionContext.builder()
                .taskId(task.getTaskId())
                .bindingId(dto.getBindingId())
                .overrideContextJson(dto.getOverrideContextJson())
                .triggerType(dto.getTriggerType())
                .retryMode(false)
                .build();
        strategy.execute(executionContext);
        if (executionContext.getAttempt() == null || !StringUtils.hasText(executionContext.getAttempt().getAttemptId())) {
            throw new WorkException("发布执行未生成 attemptId");
        }
        return executionContext.getAttempt().getAttemptId();
    }

    private void retryByBTree(XhsPublishTaskEntity task, XhsPublishRetryCommandEntity dto) {
        IXhsPublishExecuteStrategy strategy = requireExecuteStrategy(task);
        XhsPublishExecutionContext executionContext = XhsPublishExecutionContext.builder()
                .taskId(task.getTaskId())
                .overrideContextJson(dto.getOverrideContextJson())
                .triggerType(dto.getTriggerType())
                .retryMode(true)
                .build();
        strategy.execute(executionContext);
        if (executionContext.getAttempt() == null || !StringUtils.hasText(executionContext.getAttempt().getAttemptId())) {
            throw new WorkException("重试执行未生成 attemptId");
        }
    }

    private IXhsPublishExecuteStrategy requireExecuteStrategy(XhsPublishTaskEntity task) {
        IXhsPublishExecuteStrategy strategy = executeStrategyFactory.getStrategy(task.getPublishMode(), task.getPublishType());
        if (strategy == null) {
            throw new WorkException("当前发布模式/类型尚未接入执行策略");
        }
        return strategy;
    }

    private void enterCopyReviewPending(XhsPublishTaskEntity task, XhsPublishAttemptEntity attempt) {
        // A 模式首轮提交先进入文案审核阻断，审核通过后再恢复执行树。
        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "copy_review_required"));
        publishRepository.saveTask(task);

        attempt.setAttemptStatus(taskStateMachine.nextAttemptStatus(attempt.getAttemptStatus(), "copy_review_required"));
        attempt.setStage(task.getCurrentStage());
        publishRepository.saveAttempt(attempt);

        XhsPublishReviewEntity review = XhsPublishReviewEntity.builder()
                .reviewId(idSupport.nextReviewId())
                .taskId(task.getTaskId())
                .attemptId(attempt.getAttemptId())
                .reviewStage(ReviewStageVO.copy_review.name())
                .reviewMode("manual")
                .reviewStatus(ReviewStatusVO.pending.name())
                .reviewerId(taskAccessSupport.requireUserId())
                .reviewComment("A模式文案审核待处理")
                .decidedTime(LocalDateTime.now())
                .build();
        publishRepository.saveReview(review);
    }

}
