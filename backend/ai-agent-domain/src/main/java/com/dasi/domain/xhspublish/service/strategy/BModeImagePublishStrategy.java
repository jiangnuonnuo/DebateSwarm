package com.dasi.domain.xhspublish.service.strategy;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.config.XhsPublishProperties;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishSnapshotEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.port.IXhsMcpPort;
import com.dasi.domain.xhspublish.port.IXhsSnapshotStoragePort;
import com.dasi.domain.xhspublish.repository.IXhsPublishAttemptRepository;
import com.dasi.domain.xhspublish.repository.IXhsPublishSnapshotRepository;
import com.dasi.domain.xhspublish.repository.IXhsPublishTaskRepository;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.domain.IPublishTaskStateMachine;
import com.dasi.domain.xhspublish.service.domain.IPublishValidationDomainService;
import com.dasi.domain.xhspublish.service.rule.XhsPublishPayloadRuleChain;
import com.dasi.domain.xhspublish.service.rule.XhsPublishRuleContext;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
public class BModeImagePublishStrategy implements IXhsPublishExecuteStrategy {

    @Resource
    private XhsPublishPayloadRuleChain payloadRuleChain;

    @Resource
    private IPublishValidationDomainService validationDomainService;

    @Resource
    private IXhsMcpPort xhsMcpPort;

    @Resource
    private IXhsPublishTaskRepository taskRepository;

    @Resource
    private IXhsPublishAttemptRepository attemptRepository;

    @Resource
    private IXhsPublishSnapshotRepository snapshotRepository;

    @Resource
    private IXhsSnapshotStoragePort snapshotStoragePort;

    @Resource
    private IPublishTaskStateMachine taskStateMachine;

    @Resource
    private XhsPublishIdSupport idSupport;

    @Resource
    private XhsPublishProperties xhsPublishProperties;

    @Override
    public String getKey() {
        return XhsPublishExecuteStrategyFactory.buildKey("B", "image");
    }

    @Override
    public void execute(XhsPublishExecutionContext executionContext) {
        XhsPublishTaskEntity task = executionContext.getTask();
        XhsPublishAttemptEntity attempt = executionContext.getAttempt();
        LocalDateTime startTime = LocalDateTime.now();

        try {
            task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "start_execute"));
            task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "start_execute"));
            taskRepository.update(task);

            attempt.setAttemptStatus("running");
            attempt.setStage(task.getCurrentStage());
            attempt.setStartTime(startTime);
            attemptRepository.update(attempt);

            XhsPublishRuleContext ruleContext = XhsPublishRuleContext.builder()
                    .task(task)
                    .attempt(attempt)
                    .binding(executionContext.getBinding())
                    .assetList(executionContext.getAssetList())
                    .sourceContext(executionContext.getSourceContext())
                    .build();
            payloadRuleChain.apply(ruleContext);

            String publishRequestJson = JSON.toJSONString(ruleContext.getPublishPayload());
            validationDomainService.validatePublishPayload(task.getPublishType(), publishRequestJson);

            task.setLatestContextJson(publishRequestJson);
            task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "payload_built"));
            taskRepository.update(task);

            attempt.setStage(task.getCurrentStage());
            attempt.setContextJson(JSON.toJSONString(executionContext.getSourceContext()));
            attempt.setPublishRequestJson(publishRequestJson);
            attemptRepository.update(attempt);

            XhsPublishSnapshotEntity payloadSnapshot = saveSnapshot(task, attempt, attempt.getAttemptNo(), task.getCurrentStage(), publishRequestJson);

            task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "publish_submitted"));
            taskRepository.update(task);
            attempt.setStage(task.getCurrentStage());
            attemptRepository.update(attempt);

            String submitResultJson = xhsMcpPort.submitImagePublish(publishRequestJson);
            JSONObject finalResult = pollUntilSettled(submitResultJson);
            String finalResultJson = JSON.toJSONString(finalResult);

            attempt.setPublishResultJson(finalResultJson);
            attempt.setEndTime(LocalDateTime.now());
            attempt.setDurationMs(Duration.between(startTime, attempt.getEndTime()).toMillis());
            attempt.setCostAmount(BigDecimal.ZERO);

            String finalStatus = extractStatus(finalResult);
            task.setFinalResultJson(finalResultJson);

            if ("failed".equalsIgnoreCase(finalStatus)) {
                task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "publish_failed"));
                task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "publish_failed"));
                attempt.setAttemptStatus("failed");
                attempt.setErrorCode(finalResult.getString("error_code"));
                attempt.setErrorMessage(extractErrorMessage(finalResult));
                attempt.setRetryable(1);
                taskRepository.update(task);
                attemptRepository.update(attempt);
                keepSnapshot(payloadSnapshot);
                saveSnapshot(task, attempt, attempt.getAttemptNo() + 1000, "publish_failed", finalResultJson);
                return;
            }

            if ("published".equalsIgnoreCase(finalStatus) || "verified".equalsIgnoreCase(finalStatus) || "succeeded".equalsIgnoreCase(finalStatus)) {
                task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "publish_succeeded"));
                task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "publish_succeeded"));
                attempt.setAttemptStatus("published");
            } else {
                task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "publish_accepted"));
                task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "publish_accepted"));
                attempt.setAttemptStatus("accepted");
            }
            attempt.setRetryable(0);
            taskRepository.update(task);
            attemptRepository.update(attempt);

            deleteSnapshot(payloadSnapshot);
            XhsPublishSnapshotEntity successSnapshot = saveSnapshot(task, attempt, attempt.getAttemptNo() + 2000, "publish_result", finalResultJson);
            deleteSnapshot(successSnapshot);
        } catch (Exception e) {
            log.error("【小红书发布】B 模式图文发布失败：taskId={}, attemptId={}", task.getTaskId(), attempt.getAttemptId(), e);
            task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "publish_failed"));
            task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "publish_failed"));
            task.setFinalResultJson(buildFailureResult(e.getMessage()));
            taskRepository.update(task);

            attempt.setAttemptStatus("failed");
            attempt.setErrorMessage(e.getMessage());
            attempt.setRetryable(1);
            attempt.setEndTime(LocalDateTime.now());
            attempt.setDurationMs(Duration.between(startTime, attempt.getEndTime()).toMillis());
            attemptRepository.update(attempt);

            saveSnapshot(task, attempt, attempt.getAttemptNo() + 1000, "publish_failed", buildFailureResult(e.getMessage()));
        }
    }

    private JSONObject pollUntilSettled(String submitResultJson) {
        JSONObject submitResult = JSON.parseObject(submitResultJson);
        String jobId = submitResult.getString("job_id");
        if (jobId == null || jobId.isBlank()) {
            return submitResult;
        }

        JSONObject latest = submitResult;
        for (int i = 0; i < defaultInt(xhsPublishProperties.getMcpPollRounds(), 2); i++) {
            String statusJson = xhsMcpPort.queryJobStatus(jobId);
            latest = JSON.parseObject(statusJson);
            String status = extractStatus(latest);
            if ("failed".equalsIgnoreCase(status) || "published".equalsIgnoreCase(status) || "verified".equalsIgnoreCase(status) || "succeeded".equalsIgnoreCase(status)) {
                JSONObject verifyPayload = new JSONObject();
                verifyPayload.put("job_id", jobId);
                String verifyResultJson = xhsMcpPort.verifyPublishedNote(JSON.toJSONString(verifyPayload));
                JSONObject verifyResult = JSON.parseObject(verifyResultJson);
                latest.put("verify_result", verifyResult);
                String verifyStatus = verifyResult.getString("verify_status");
                if ("verified".equalsIgnoreCase(verifyStatus)) {
                    latest.put("status", "published");
                }
                return latest;
            }
            sleep(defaultInt(xhsPublishProperties.getMcpPollIntervalMillis(), 1500));
        }
        return latest;
    }

    private String extractStatus(JSONObject result) {
        if (result == null) {
            return "failed";
        }
        String status = result.getString("status");
        if (status != null && !status.isBlank()) {
            return status;
        }
        JSONObject verifyResult = result.getJSONObject("verify_result");
        if (verifyResult != null) {
            String verifyStatus = verifyResult.getString("verify_status");
            if (verifyStatus != null && !verifyStatus.isBlank()) {
                return verifyStatus;
            }
        }
        Boolean accepted = result.getBoolean("accepted");
        if (Boolean.TRUE.equals(accepted)) {
            return "accepted";
        }
        Boolean ok = result.getBoolean("ok");
        return Boolean.FALSE.equals(ok) ? "failed" : "accepted";
    }

    private String extractErrorMessage(JSONObject result) {
        if (result == null) {
            return "发布失败";
        }
        String errorMessage = result.getString("error_message");
        return (errorMessage == null || errorMessage.isBlank()) ? JSON.toJSONString(result) : errorMessage;
    }

    private XhsPublishSnapshotEntity saveSnapshot(XhsPublishTaskEntity task,
                                                  XhsPublishAttemptEntity attempt,
                                                  Integer roundNo,
                                                  String stage,
                                                  String content) {
        String snapshotPath = snapshotStoragePort.save(task.getTaskId(), roundNo, stage, content);
        XhsPublishSnapshotEntity entity = XhsPublishSnapshotEntity.builder()
                .snapshotId(idSupport.nextSnapshotId())
                .taskId(task.getTaskId())
                .attemptId(attempt.getAttemptId())
                .roundNo(roundNo)
                .stage(stage)
                .snapshotPath(snapshotPath)
                .cleanupStatus("pending")
                .expireTime(LocalDateTime.now().plusHours(defaultInt(xhsPublishProperties.getSnapshotRetentionHours(), 24)))
                .build();
        snapshotRepository.insert(entity);
        return snapshotRepository.queryBySnapshotId(entity.getSnapshotId());
    }

    private void keepSnapshot(XhsPublishSnapshotEntity snapshotEntity) {
        if (snapshotEntity == null) {
            return;
        }
        snapshotEntity.setCleanupStatus("pending");
        snapshotRepository.update(snapshotEntity);
    }

    private void deleteSnapshot(XhsPublishSnapshotEntity snapshotEntity) {
        if (snapshotEntity == null) {
            return;
        }
        snapshotStoragePort.delete(snapshotEntity.getSnapshotPath());
        snapshotEntity.setCleanupStatus("deleted");
        snapshotEntity.setDeletedTime(LocalDateTime.now());
        snapshotRepository.update(snapshotEntity);
    }

    private String buildFailureResult(String message) {
        JSONObject object = new JSONObject();
        object.put("status", "failed");
        object.put("error_message", message);
        return JSON.toJSONString(object);
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
