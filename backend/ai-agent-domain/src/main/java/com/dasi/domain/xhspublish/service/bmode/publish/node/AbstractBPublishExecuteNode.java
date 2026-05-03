package com.dasi.domain.xhspublish.service.bmode.publish.node;

import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;

public abstract class AbstractBPublishExecuteNode extends AbstractMultiThreadStrategyRouter<XhsPublishExecutionContext, XhsPublishExecutionContext, String> {

    @Override
    protected void multiThread(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        // B 正式发布树按单线程串行执行。
    }

}
