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
        // 步骤 3：调用 payload 规则链，把 sourceContext + assetList 收敛成 publishRequestJson。
        String publishRequestJson = payloadBuilder.build(dynamicContext);
        dynamicContext.setPublishRequestJson(publishRequestJson);

        // 步骤 3.1：回写 payload_built 生命周期，并保存 payload 快照，便于后续追踪失败现场。
        executionLifecycle.markPayloadBuilt(dynamicContext, publishRequestJson);
        dynamicContext.setPayloadSnapshot(snapshotManager.savePayloadSnapshot(dynamicContext, publishRequestJson));
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return getBean("xhsPublishRemoteNode");
    }

}
