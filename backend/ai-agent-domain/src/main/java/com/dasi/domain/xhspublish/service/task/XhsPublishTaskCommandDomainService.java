package com.dasi.domain.xhspublish.service.task;

import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.model.aggregate.XhsPublishTaskAggregate;
import com.dasi.domain.xhspublish.model.dto.CreateXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.ReviewXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.RetryXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.SubmitXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.UpdateXhsPublishTaskContextDTO;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAccountBindingEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishReviewEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.model.valobj.PublishAttemptStatusVO;
import com.dasi.domain.xhspublish.model.valobj.PublishStageVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTaskStatusVO;
import com.dasi.domain.xhspublish.service.domain.IPublishRetryDomainService;
import com.dasi.domain.xhspublish.service.domain.IPublishTaskStateMachine;
import com.dasi.domain.xhspublish.service.domain.IPublishValidationDomainService;
import com.dasi.domain.xhspublish.service.support.XhsPublishBindingSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishExecutionSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskAccessSupport;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
        XhsPublishTaskEntity task = taskAccessSupport.queryOwnedTask(dto.getTaskId());
        if (StringUtils.hasText(dto.getOverrideContextJson())) {
            validationDomainService.validateTaskRequest(task.getPublishType(), dto.getOverrideContextJson());
            task.setLatestContextJson(dto.getOverrideContextJson());
        }

        XhsPublishAccountBindingEntity binding = bindingSupport.resolveBinding(task.getUserId(),
                StringUtils.hasText(dto.getBindingId()) ? dto.getBindingId() : task.getBindingId());
        task.setBindingId(binding.getBindingId());
        task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "submit_requested"));
        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "submit_requested"));
        publishRepository.saveTask(task);

        int attemptNo = publishRepository.listAttemptByTaskId(task.getTaskId()).size() + 1;
        String contextJson = StringUtils.hasText(task.getLatestContextJson()) ? task.getLatestContextJson() : task.getRequestJson();
        XhsPublishAttemptEntity attempt = newAttempt(task, attemptNo, dto.getTriggerType(), contextJson);
        publishRepository.saveAttempt(attempt);
        XhsPublishAttemptEntity persistedAttempt = publishRepository.queryAttemptByAttemptId(attempt.getAttemptId());

        executionSupport.execute(task, persistedAttempt, binding, publishRepository.listAssetByTaskId(task.getTaskId()));
        return persistedAttempt.getAttemptId();
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
        XhsPublishTaskAggregate aggregate = taskAccessSupport.queryOwnedAggregate(dto.getTaskId());
        XhsPublishTaskEntity task = aggregate.getTask();
        List<XhsPublishAttemptEntity> attemptList = aggregate.getAttemptList();
        XhsPublishAttemptEntity latestAttempt = attemptList.isEmpty() ? null : attemptList.get(0);
        if (latestAttempt != null && !retryDomainService.canRetry(task.getRetryPolicyJson(), latestAttempt.getAttemptNo(), latestAttempt.getErrorCode())) {
            throw new WorkException("当前任务不满足重试条件");
        }

        if (StringUtils.hasText(dto.getOverrideContextJson())) {
            validationDomainService.validateTaskRequest(task.getPublishType(), dto.getOverrideContextJson());
            task.setLatestContextJson(dto.getOverrideContextJson());
            publishRepository.saveTask(task);
        }

        XhsPublishAccountBindingEntity binding = bindingSupport.resolveBinding(task.getUserId(), task.getBindingId());
        int attemptNo = attemptList.size() + 1;
        String contextJson = StringUtils.hasText(task.getLatestContextJson()) ? task.getLatestContextJson() : task.getRequestJson();
        XhsPublishAttemptEntity attempt = newAttempt(task, attemptNo, dto.getTriggerType(), contextJson);
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

}
