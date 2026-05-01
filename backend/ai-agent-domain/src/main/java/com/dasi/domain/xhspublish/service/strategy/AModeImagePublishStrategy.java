package com.dasi.domain.xhspublish.service.strategy;

import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.lifecycle.IXhsPublishExecutionLifecycle;
import com.dasi.domain.xhspublish.service.execution.node.XhsPublishRootNode;
import com.dasi.domain.xhspublish.service.execution.snapshot.IXhsPublishSnapshotManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class AModeImagePublishStrategy implements IXhsPublishExecuteStrategy {

    @Resource
    private XhsPublishRootNode xhsPublishRootNode;

    @Resource
    private IXhsPublishExecutionLifecycle executionLifecycle;

    @Resource
    private IXhsPublishSnapshotManager snapshotManager;

    @Override
    public String getKey() {
        return XhsPublishExecuteStrategyFactory.buildKey("A", "image");
    }

    @Override
    public void execute(XhsPublishExecutionContext executionContext) {
        LocalDateTime startTime = LocalDateTime.now();
        executionContext.setStartTime(startTime);

        try {
            xhsPublishRootNode.apply(executionContext, executionContext);
        } catch (Exception e) {
            log.error("【小红书发布】A 模式图文发布失败：taskId={}, attemptId={}",
                    executionContext.getTask().getTaskId(),
                    executionContext.getAttempt().getAttemptId(),
                    e);
            executionLifecycle.markExecutionException(executionContext, e, startTime);
            snapshotManager.saveFailureSnapshot(executionContext, buildFailureResult(e.getMessage()));
        }
    }

    private String buildFailureResult(String message) {
        return "{\"status\":\"failed\",\"error_message\":\"" + String.valueOf(message).replace("\"", "\\\"") + "\"}";
    }
}
