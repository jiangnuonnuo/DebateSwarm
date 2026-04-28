package com.dasi.domain.xhspublish.service.execution.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.lifecycle.IXhsPublishExecutionLifecycle;
import com.dasi.domain.xhspublish.service.execution.remote.IXhsPublishRemoteExecutor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("xhsPublishRemoteNode")
public class XhsPublishRemoteNode extends AbstractXhsPublishNode {

    @Resource
    private IXhsPublishExecutionLifecycle executionLifecycle;

    @Resource
    private IXhsPublishRemoteExecutor remoteExecutor;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        // 节点顺序固定：先回写 publish_submitted，再发起远端调用，保证状态与外部交互一致。
        executionLifecycle.markPublishSubmitted(dynamicContext);
        long remoteStart = System.currentTimeMillis();
        dynamicContext.setRemoteResult(remoteExecutor.executeImagePublish(dynamicContext.getPublishRequestJson()));
        long durationMs = Math.max(0, System.currentTimeMillis() - remoteStart);
        dynamicContext.setRemoteDurationMs(durationMs);
        log.info("【小红书发布】event=remote_called taskId={} attemptId={} errorCode={} durationMs={}",
                dynamicContext.getTask().getTaskId(),
                dynamicContext.getAttempt().getAttemptId(),
                dynamicContext.getRemoteResult() == null ? "-" : dynamicContext.getRemoteResult().getErrorCode(),
                durationMs);
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return getBean("xhsPublishResultNode");
    }

}
