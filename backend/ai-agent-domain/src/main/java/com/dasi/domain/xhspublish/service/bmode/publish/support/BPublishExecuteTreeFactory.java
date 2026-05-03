package com.dasi.domain.xhspublish.service.bmode.publish.support;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.bmode.publish.node.BPublishExecuteRootNode;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class BPublishExecuteTreeFactory {

    @Resource
    private BPublishExecuteRootNode rootNode;

    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> getRootNode() {
        // B 正式发布树固定链路：
        // Root -> TaskLoad -> BindingResolve -> PreflightValidate -> AttemptCreate
        // -> PayloadAssemble -> McpSubmit -> McpResultRoute
        // -> (AcceptedPersist | SuccessPersist | FailurePersist) -> SnapshotFinalize
        return rootNode;
    }

}
