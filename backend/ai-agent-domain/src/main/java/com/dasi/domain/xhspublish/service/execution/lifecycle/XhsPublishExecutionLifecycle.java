package com.dasi.domain.xhspublish.service.execution.lifecycle;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.domain.IPublishTaskStateMachine;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class XhsPublishExecutionLifecycle implements IXhsPublishExecutionLifecycle {

    @Resource
    private IXhsPublishRepository publishRepository;

    @Resource
    private IPublishTaskStateMachine taskStateMachine;

    @Override
    public void markExecutionStarted(XhsPublishExecutionContext executionContext, LocalDateTime startTime) {
        XhsPublishTaskEntity task = executionContext.getTask();
        XhsPublishAttemptEntity attempt = executionContext.getAttempt();

        task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "start_execute"));
        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "start_execute"));
        publishRepository.saveTask(task);

        attempt.setAttemptStatus(taskStateMachine.nextAttemptStatus(attempt.getAttemptStatus(), "start_execute"));
        attempt.setStage(task.getCurrentStage());
        attempt.setStartTime(startTime);
        publishRepository.saveAttempt(attempt);
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
    }

    @Override
    public void markPublishSubmitted(XhsPublishExecutionContext executionContext) {
        XhsPublishTaskEntity task = executionContext.getTask();
        XhsPublishAttemptEntity attempt = executionContext.getAttempt();

        task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "publish_submitted"));
        publishRepository.saveTask(task);

        attempt.setStage(task.getCurrentStage());
        publishRepository.saveAttempt(attempt);
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
        attempt.setRetryable(1);
        attempt.setErrorMessage(exception == null ? "发布失败" : exception.getMessage());
        attempt.setPublishResultJson(resultJson);
        applyAttemptFinishTime(attempt, startTime);
        publishRepository.saveAttempt(attempt);
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

}

