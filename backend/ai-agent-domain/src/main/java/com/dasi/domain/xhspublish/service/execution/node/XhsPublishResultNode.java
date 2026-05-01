package com.dasi.domain.xhspublish.service.execution.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.model.entity.XhsPublishSnapshotEntity;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.lifecycle.IXhsPublishExecutionLifecycle;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;
import com.dasi.domain.xhspublish.service.execution.snapshot.IXhsPublishSnapshotManager;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service("xhsPublishResultNode")
public class XhsPublishResultNode extends AbstractXhsPublishNode {

    @Resource
    private IXhsPublishExecutionLifecycle executionLifecycle;

    @Resource
    private IXhsPublishSnapshotManager snapshotManager;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        // 步骤 5：结果节点只做结果分流，不再回头改 payload 或发起远端调用。
        XhsPublishRemoteResult remoteResult = dynamicContext.getRemoteResult();
        if (remoteResult == null) {
            throw new WorkException("发布执行结果为空");
        }

        XhsPublishSnapshotEntity payloadSnapshot = dynamicContext.getPayloadSnapshot();
        // 结果分流语义：failed -> 保留 payload + 落失败快照；published -> 清理快照；accepted -> 保留等待后续确认。
        if (remoteResult.isFailed()) {
            executionLifecycle.markPublishFailed(dynamicContext, remoteResult, dynamicContext.getStartTime());
            snapshotManager.keep(payloadSnapshot);
            snapshotManager.saveFailureSnapshot(dynamicContext, remoteResult.getResultJson());
            return router(requestParameter, dynamicContext);
        }

        if (remoteResult.isPublished()) {
            executionLifecycle.markPublishSucceeded(dynamicContext, remoteResult, dynamicContext.getStartTime());
            snapshotManager.delete(payloadSnapshot);
            XhsPublishSnapshotEntity resultSnapshot = snapshotManager.saveResultSnapshot(dynamicContext, remoteResult.getResultJson());
            snapshotManager.delete(resultSnapshot);
            return router(requestParameter, dynamicContext);
        }

        executionLifecycle.markPublishAccepted(dynamicContext, remoteResult, dynamicContext.getStartTime());
        snapshotManager.keep(payloadSnapshot);
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return defaultStrategyHandler;
    }

}
