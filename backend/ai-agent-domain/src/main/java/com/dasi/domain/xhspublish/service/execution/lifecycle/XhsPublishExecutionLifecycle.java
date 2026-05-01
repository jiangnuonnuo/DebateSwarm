package com.dasi.domain.xhspublish.service.execution.lifecycle;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.domain.IPublishTaskStateMachine;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;
import com.dasi.domain.xhspublish.service.knowledge.XhsPublishKnowledgeIngestionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
public class XhsPublishExecutionLifecycle implements IXhsPublishExecutionLifecycle {

    @Resource
    private IXhsPublishRepository publishRepository;

    @Resource
    private IPublishTaskStateMachine taskStateMachine;

    @Resource
    private XhsPublishKnowledgeIngestionService knowledgeIngestionService;

    @Override
    public void markExecutionStarted(XhsPublishExecutionContext executionContext, LocalDateTime startTime) {
        XhsPublishTaskEntity task = executionContext.getTask();
        XhsPublishAttemptEntity attempt = executionContext.getAttempt();

        // 生命周期入口：先推进 task/stage，再把 attempt 标记为 running，确保三轴状态一致。
        task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "start_execute"));
        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "start_execute"));
        publishRepository.saveTask(task);

        attempt.setAttemptStatus(taskStateMachine.nextAttemptStatus(attempt.getAttemptStatus(), "start_execute"));
        attempt.setStage(task.getCurrentStage());
        attempt.setStartTime(startTime);
        publishRepository.saveAttempt(attempt);
        logEvent(task.getTaskId(), attempt.getAttemptId(), "start_execute", null, 0L, false);
    }

    @Override
    public void markPayloadBuilt(XhsPublishExecutionContext executionContext, String publishRequestJson) {
        XhsPublishTaskEntity task = executionContext.getTask();
        XhsPublishAttemptEntity attempt = executionContext.getAttempt();

        task.setLatestContextJson(publishRequestJson);
        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "payload_built"));
        publishRepository.saveTask(task);

        attempt.setStage(task.getCurrentStage());
        attempt.setContextJson(JSON.toJSONString(executionContext.getSourceContext()));
        attempt.setPublishRequestJson(publishRequestJson);
        publishRepository.saveAttempt(attempt);
        logEvent(task.getTaskId(), attempt.getAttemptId(), "payload_built", null, 0L, false);
    }

    @Override
    public void markPublishSubmitted(XhsPublishExecutionContext executionContext) {
        XhsPublishTaskEntity task = executionContext.getTask();
        XhsPublishAttemptEntity attempt = executionContext.getAttempt();

        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "publish_submitted"));
        publishRepository.saveTask(task);

        attempt.setStage(task.getCurrentStage());
        publishRepository.saveAttempt(attempt);
        Long remoteDurationMs = executionContext.getRemoteDurationMs();
        logEvent(task.getTaskId(), attempt.getAttemptId(), "publish_submitted", null, remoteDurationMs == null ? 0L : remoteDurationMs, false);
    }

    @Override
    public void markPublishAccepted(XhsPublishExecutionContext executionContext, XhsPublishRemoteResult remoteResult, LocalDateTime startTime) {
        XhsPublishTaskEntity task = executionContext.getTask();
        XhsPublishAttemptEntity attempt = executionContext.getAttempt();

        task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "publish_accepted"));
        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "publish_accepted"));
        task.setFinalResultJson(remoteResult.getResultJson());
        publishRepository.saveTask(task);

        attempt.setAttemptStatus(taskStateMachine.nextAttemptStatus(attempt.getAttemptStatus(), "publish_accepted"));
        applyAttemptCompletion(attempt, remoteResult, startTime, 0);
        publishRepository.saveAttempt(attempt);
        logEvent(task.getTaskId(), attempt.getAttemptId(), "publish_accepted", remoteResult.getErrorCode(), attempt.getDurationMs(), false);
    }

    @Override
    public void markPublishSucceeded(XhsPublishExecutionContext executionContext, XhsPublishRemoteResult remoteResult, LocalDateTime startTime) {
        XhsPublishTaskEntity task = executionContext.getTask();
        XhsPublishAttemptEntity attempt = executionContext.getAttempt();

        task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "publish_succeeded"));
        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "publish_succeeded"));
        task.setFinalResultJson(remoteResult.getResultJson());
        publishRepository.saveTask(task);

        attempt.setAttemptStatus(taskStateMachine.nextAttemptStatus(attempt.getAttemptStatus(), "publish_succeeded"));
        attempt.setRetryable(0);
        applyAttemptCompletion(attempt, remoteResult, startTime, 0);
        publishRepository.saveAttempt(attempt);
        // 发布成功后的知识沉淀采用 best-effort，不反向影响已成功的主链路状态。
        knowledgeIngestionService.ingestPublishSuccess(task, attempt, remoteResult);
        logEvent(task.getTaskId(), attempt.getAttemptId(), "publish_succeeded", remoteResult.getErrorCode(), attempt.getDurationMs(), false);
    }

    @Override
    public void markPublishFailed(XhsPublishExecutionContext executionContext, XhsPublishRemoteResult remoteResult, LocalDateTime startTime) {
        XhsPublishTaskEntity task = executionContext.getTask();
        XhsPublishAttemptEntity attempt = executionContext.getAttempt();

        task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "publish_failed"));
        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "publish_failed"));
        task.setFinalResultJson(remoteResult.getResultJson());
        publishRepository.saveTask(task);

        attempt.setAttemptStatus(taskStateMachine.nextAttemptStatus(attempt.getAttemptStatus(), "publish_failed"));
        attempt.setRetryable(1);
        applyAttemptCompletion(attempt, remoteResult, startTime, 1);
        publishRepository.saveAttempt(attempt);
        logEvent(task.getTaskId(), attempt.getAttemptId(), "publish_failed", remoteResult.getErrorCode(), attempt.getDurationMs(), true);
    }

    @Override
    public void markExecutionException(XhsPublishExecutionContext executionContext, Exception exception, LocalDateTime startTime) {
        XhsPublishTaskEntity task = executionContext.getTask();
        XhsPublishAttemptEntity attempt = executionContext.getAttempt();
        String resultJson = buildFailureResult(exception == null ? "发布失败" : exception.getMessage());

        task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "publish_failed"));
        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "publish_failed"));
        task.setFinalResultJson(resultJson);
        publishRepository.saveTask(task);

        attempt.setAttemptStatus(taskStateMachine.nextAttemptStatus(attempt.getAttemptStatus(), "publish_failed"));
        attempt.setStage(task.getCurrentStage());
        attempt.setRetryable(1);
        attempt.setErrorMessage(exception == null ? "发布失败" : exception.getMessage());
        attempt.setPublishResultJson(resultJson);
        applyAttemptFinishTime(attempt, startTime);
        publishRepository.saveAttempt(attempt);
        logEvent(task.getTaskId(), attempt.getAttemptId(), "publish_exception", "EXECUTION_EXCEPTION", attempt.getDurationMs(), true);
    }

    private void applyAttemptCompletion(XhsPublishAttemptEntity attempt,
                                        XhsPublishRemoteResult remoteResult,
                                        LocalDateTime startTime,
                                        int retryable) {
        attempt.setPublishResultJson(remoteResult.getResultJson());
        attempt.setErrorCode(remoteResult.getErrorCode());
        attempt.setErrorMessage(remoteResult.getErrorMessage());
        attempt.setRetryable(retryable);
        attempt.setCostAmount(BigDecimal.ZERO);
        applyAttemptFinishTime(attempt, startTime);
    }

    private void applyAttemptFinishTime(XhsPublishAttemptEntity attempt, LocalDateTime startTime) {
        attempt.setEndTime(LocalDateTime.now());
        attempt.setDurationMs(Duration.between(startTime, attempt.getEndTime()).toMillis());
    }

    private String buildFailureResult(String message) {
        JSONObject object = new JSONObject();
        object.put("status", "failed");
        object.put("error_message", message);
        return JSON.toJSONString(object);
    }

    private void logEvent(String taskId, String attemptId, String event, String errorCode, Long durationMs, boolean failed) {
        String safeErrorCode = errorCode == null || errorCode.isBlank() ? "-" : errorCode;
        long safeDuration = durationMs == null || durationMs < 0 ? 0L : durationMs;
        if (failed) {
            log.warn("【小红书发布】event={} taskId={} attemptId={} errorCode={} durationMs={}",
                    event, taskId, attemptId, safeErrorCode, safeDuration);
            return;
        }
        log.info("【小红书发布】event={} taskId={} attemptId={} errorCode={} durationMs={}",
                event, taskId, attemptId, safeErrorCode, safeDuration);
    }

}

