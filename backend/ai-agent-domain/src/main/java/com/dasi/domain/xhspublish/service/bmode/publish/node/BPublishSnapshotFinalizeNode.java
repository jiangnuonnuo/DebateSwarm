package com.dasi.domain.xhspublish.service.bmode.publish.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.model.entity.XhsPublishSnapshotEntity;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.snapshot.IXhsPublishSnapshotManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("bPublishSnapshotFinalizeNode")
public class BPublishSnapshotFinalizeNode extends AbstractBPublishExecuteNode {

    @Resource
    private IXhsPublishSnapshotManager snapshotManager;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        // 终态节点只负责快照生命周期：
        // failure: 保留 payload 快照 + 额外落失败结果快照
        // success: 删除 payload 快照 + 落结果快照后立即清理
        // accepted: 保留 payload 快照，等待后续人工核验
        XhsPublishSnapshotEntity payloadSnapshot = dynamicContext.getPayloadSnapshot();
        if ("failure".equals(dynamicContext.getResultBranch())) {
            snapshotManager.keep(payloadSnapshot);
            snapshotManager.saveFailureSnapshot(dynamicContext, dynamicContext.getRemoteResult().getResultJson());
            log.info("【小红书发布】B正式发布-失败快照已保留：taskId={}, attemptId={}",
                    dynamicContext.getTask().getTaskId(),
                    dynamicContext.getAttempt().getAttemptId());
            return router(requestParameter, dynamicContext);
        }

        if ("success".equals(dynamicContext.getResultBranch())) {
            snapshotManager.delete(payloadSnapshot);
            XhsPublishSnapshotEntity resultSnapshot = snapshotManager.saveResultSnapshot(dynamicContext, dynamicContext.getRemoteResult().getResultJson());
            snapshotManager.delete(resultSnapshot);
            log.info("【小红书发布】B正式发布-成功快照已清理：taskId={}, attemptId={}",
                    dynamicContext.getTask().getTaskId(),
                    dynamicContext.getAttempt().getAttemptId());
            return router(requestParameter, dynamicContext);
        }

        snapshotManager.keep(payloadSnapshot);
        log.info("【小红书发布】B正式发布-受理态快照保留：taskId={}, attemptId={}",
                dynamicContext.getTask().getTaskId(),
                dynamicContext.getAttempt().getAttemptId());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return defaultStrategyHandler;
    }

}
