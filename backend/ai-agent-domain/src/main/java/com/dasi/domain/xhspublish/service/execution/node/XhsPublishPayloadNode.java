package com.dasi.domain.xhspublish.service.execution.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.lifecycle.IXhsPublishExecutionLifecycle;
import com.dasi.domain.xhspublish.service.execution.payload.IXhsPublishPayloadBuilder;
import com.dasi.domain.xhspublish.service.execution.snapshot.IXhsPublishSnapshotManager;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service("xhsPublishPayloadNode")
public class XhsPublishPayloadNode extends AbstractXhsPublishNode {

    @Resource
    private IXhsPublishPayloadBuilder payloadBuilder;

    @Resource
    private IXhsPublishExecutionLifecycle executionLifecycle;

    @Resource
    private IXhsPublishSnapshotManager snapshotManager;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        String publishRequestJson = payloadBuilder.build(dynamicContext);
        dynamicContext.setPublishRequestJson(publishRequestJson);
        executionLifecycle.markPayloadBuilt(dynamicContext, publishRequestJson);
        dynamicContext.setPayloadSnapshot(snapshotManager.savePayloadSnapshot(dynamicContext, publishRequestJson));
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return getBean("xhsPublishRemoteNode");
    }

}
