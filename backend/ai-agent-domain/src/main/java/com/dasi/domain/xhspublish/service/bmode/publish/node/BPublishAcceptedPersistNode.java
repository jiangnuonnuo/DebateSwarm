package com.dasi.domain.xhspublish.service.bmode.publish.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.lifecycle.IXhsPublishExecutionLifecycle;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("bPublishAcceptedPersistNode")
public class BPublishAcceptedPersistNode extends AbstractBPublishExecuteNode {

    @Resource
    private IXhsPublishExecutionLifecycle executionLifecycle;

    @Resource
    private BPublishSnapshotFinalizeNode snapshotFinalizeNode;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        executionLifecycle.markPublishAccepted(dynamicContext, dynamicContext.getRemoteResult(), dynamicContext.getStartTime());
        log.info("【小红书发布】B正式发布-结果已受理：taskId={}, attemptId={}",
                dynamicContext.getTask().getTaskId(),
                dynamicContext.getAttempt().getAttemptId());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return snapshotFinalizeNode;
    }

}
