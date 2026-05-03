package com.dasi.domain.xhspublish.service.strategy;

import com.dasi.domain.xhspublish.service.bmode.publish.support.BPublishExecuteTreeFactory;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.lifecycle.IXhsPublishExecutionLifecycle;
import com.dasi.domain.xhspublish.service.execution.snapshot.IXhsPublishSnapshotManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class BModeImagePublishStrategy implements IXhsPublishExecuteStrategy {

    @Resource
    private BPublishExecuteTreeFactory executeTreeFactory;

    @Resource
    private IXhsPublishExecutionLifecycle executionLifecycle;

    @Resource
    private IXhsPublishSnapshotManager snapshotManager;

    @Override
    public String getKey() {
        return XhsPublishExecuteStrategyFactory.buildKey("B", "image");
    }

    @Override
    public void execute(XhsPublishExecutionContext executionContext) {
        // B:image 策略只负责启动 B 正式发布树；具体步骤全部交给节点树处理。
        LocalDateTime startTime = LocalDateTime.now();
        executionContext.setStartTime(startTime);

        try {
            executeTreeFactory.getRootNode().apply(executionContext, executionContext);
        } catch (Exception e) {
            if (executionContext.getTask() == null || executionContext.getAttempt() == null) {
                throw new RuntimeException(e);
            }
            log.error("【小红书发布】B 模式图文发布失败：taskId={}, attemptId={}",
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
