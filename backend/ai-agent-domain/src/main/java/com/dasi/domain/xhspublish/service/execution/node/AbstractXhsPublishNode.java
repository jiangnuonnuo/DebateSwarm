package com.dasi.domain.xhspublish.service.execution.node;

import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;

public abstract class AbstractXhsPublishNode extends AbstractMultiThreadStrategyRouter<XhsPublishExecutionContext, XhsPublishExecutionContext, String> {

    @Override
    protected void multiThread(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        // 小红书发布链路当前按单线串行执行，保留节点树扩展点。
    }

}
