package com.dasi.domain.xhspublish.service.task;

import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.model.dto.CreateXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.ReviewXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.RetryXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.SubmitXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.UpdateXhsPublishTaskContextDTO;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAccountBindingEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptStatsEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishReviewEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.model.valobj.PublishAttemptStatusVO;
import com.dasi.domain.xhspublish.model.valobj.PublishStageVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTaskStatusVO;
import com.dasi.domain.xhspublish.service.domain.IPublishRetryDomainService;
import com.dasi.domain.xhspublish.service.domain.IPublishTaskStateMachine;
import com.dasi.domain.xhspublish.service.domain.IPublishValidationDomainService;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionContext;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;
import com.dasi.domain.xhspublish.service.support.XhsPublishBindingSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishExecutionSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskAccessSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskLockSupport;
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

    public String createTask(CreateXhsPublishTaskDTO dto) {
        Long userId = taskAccessSupport.requireUserId();
        taskAccessSupport.validateModeAndType(dto.getPublishMode(), dto.getPublishType());
        validationDomainService.validateTaskRequest(dto.getPublishType(), dto.getRequestJson());

        XhsPublishAccountBindingEntity binding = bindingSupport.resolveBinding(userId, dto.getBindingId());
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
                .requestJson(dto.getRequestJson())
                .latestContextJson(dto.getRequestJson())
                .retryPolicyJson(dto.getRetryPolicyJson())
                .build();
        publishRepository.saveTask(task);
        return taskId;
    }

    public void updateTaskContext(UpdateXhsPublishTaskContextDTO dto) {
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

    public String submitTask(SubmitXhsPublishTaskDTO dto) {
        return taskLockSupport.withTaskLock(dto.getTaskId(), () -> doSubmitTask(dto));
    }

    @Transactional(rollbackFor = Exception.class)
    public void reviewTask(ReviewXhsPublishTaskDTO dto) {
        XhsPublishTaskEntity task = taskAccessSupport.queryOwnedTask(dto.getTaskId());
        XhsPublishReviewEntity entity = XhsPublishReviewEntity.builder()
                .reviewId(idSupport.nextReviewId())
                .taskId(task.getTaskId())
                .attemptId(dto.getAttemptId())
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
    }

    public void retryTask(RetryXhsPublishTaskDTO dto) {
        taskLockSupport.withTaskLock(dto.getTaskId(), () -> doRetryTask(dto));
    }

    private String doSubmitTask(SubmitXhsPublishTaskDTO dto) {
        // submit 入口负责做并发防重 + 任务状态迁移 + attempt 创建，之后统一交给执行树处理。
        XhsPublishTaskEntity task = taskAccessSupport.queryOwnedTask(dto.getTaskId());
        if (StringUtils.hasText(dto.getOverrideContextJson())) {
            validationDomainService.validateTaskRequest(task.getPublishType(), dto.getOverrideContextJson());
            task.setLatestContextJson(dto.getOverrideContextJson());
        }

        XhsPublishAccountBindingEntity binding = bindingSupport.resolveBinding(task.getUserId(),
                StringUtils.hasText(dto.getBindingId()) ? dto.getBindingId() : task.getBindingId());
        List<XhsPublishAttemptEntity> attemptList = publishRepository.listAttemptByTaskId(task.getTaskId());
        ensureNoActiveAttempt(attemptList);

        task.setBindingId(binding.getBindingId());
        task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "submit_requested"));
        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "submit_requested"));
        publishRepository.saveTask(task);

        int attemptNo = attemptList.size() + 1;
        String contextJson = StringUtils.hasText(task.getLatestContextJson()) ? task.getLatestContextJson() : task.getRequestJson();
        XhsPublishAttemptEntity attempt = newAttempt(task, attemptNo, safeTriggerType(dto.getTriggerType(), "submit"), contextJson);
        publishRepository.saveAttempt(attempt);
        XhsPublishAttemptEntity persistedAttempt = publishRepository.queryAttemptByAttemptId(attempt.getAttemptId());

        executionSupport.execute(task, persistedAttempt, binding, publishRepository.listAssetByTaskId(task.getTaskId()));
        return persistedAttempt.getAttemptId();
    }

    private void doRetryTask(RetryXhsPublishTaskDTO dto) {
        // retry 入口只消费「最新 attempt + 聚合统计 + 责任链决策」，避免散落 if-else 判断。
        XhsPublishTaskEntity task = taskAccessSupport.queryOwnedTask(dto.getTaskId());
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
            throw new WorkException("当前任务不满足重试条件：" + retryDecision.getReasonCode());
        }

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

}
