package com.dasi.domain.xhspublish.service.bmode.publish.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("bPublishMcpResultRouteNode")
public class BPublishMcpResultRouteNode extends AbstractBPublishExecuteNode {

    @Resource
    private BPublishSuccessPersistNode successPersistNode;

    @Resource
    private BPublishAcceptedPersistNode acceptedPersistNode;

    @Resource
    private BPublishFailurePersistNode failurePersistNode;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        XhsPublishRemoteResult remoteResult = dynamicContext.getRemoteResult();
        if (remoteResult == null) {
            throw new WorkException("发布执行结果为空");
        }
        if (remoteResult.isFailed()) {
            dynamicContext.setResultBranch("failure");
        } else if (remoteResult.isPublished()) {
            dynamicContext.setResultBranch("success");
        } else {
            dynamicContext.setResultBranch("accepted");
        }
        log.info("【小红书发布】B正式发布-结果分流：taskId={}, attemptId={}, branch={}",
                dynamicContext.getTask().getTaskId(),
                dynamicContext.getAttempt().getAttemptId(),
                dynamicContext.getResultBranch());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return switch (dynamicContext.getResultBranch()) {
            case "success" -> successPersistNode;
            case "accepted" -> acceptedPersistNode;
            default -> failurePersistNode;
        };
    }

}
