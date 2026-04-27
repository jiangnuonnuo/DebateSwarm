package com.dasi.domain.xhspublish.service.execution.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.lifecycle.IXhsPublishExecutionLifecycle;
import com.dasi.domain.xhspublish.service.execution.remote.IXhsPublishRemoteExecutor;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service("xhsPublishRemoteNode")
public class XhsPublishRemoteNode extends AbstractXhsPublishNode {

    @Resource
    private IXhsPublishExecutionLifecycle executionLifecycle;

    @Resource
    private IXhsPublishRemoteExecutor remoteExecutor;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        executionLifecycle.markPublishSubmitted(dynamicContext);
        dynamicContext.setRemoteResult(remoteExecutor.executeImagePublish(dynamicContext.getPublishRequestJson()));
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return getBean("xhsPublishResultNode");
    }

}
