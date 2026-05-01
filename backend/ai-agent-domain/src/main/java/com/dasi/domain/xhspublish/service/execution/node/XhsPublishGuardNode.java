package com.dasi.domain.xhspublish.service.execution.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.guard.XhsPublishExecutionGuardChain;
import com.dasi.domain.xhspublish.service.execution.lifecycle.IXhsPublishExecutionLifecycle;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service("xhsPublishGuardNode")
public class XhsPublishGuardNode extends AbstractXhsPublishNode {

    @Resource
    private XhsPublishExecutionGuardChain executionGuardChain;

    @Resource
    private IXhsPublishExecutionLifecycle executionLifecycle;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        // 步骤 1：执行发布前守卫链，校验 task/attempt/binding/sourceContext 是否齐全。
        executionGuardChain.guard(dynamicContext);

        // 步骤 2：守卫通过后，统一把 task/attempt 推进到 start_execute/running。
        executionLifecycle.markExecutionStarted(dynamicContext, dynamicContext.getStartTime());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return getBean("xhsPublishPayloadNode");
    }

}
