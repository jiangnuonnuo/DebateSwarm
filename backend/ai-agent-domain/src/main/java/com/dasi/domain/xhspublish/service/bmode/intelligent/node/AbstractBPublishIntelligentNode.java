package com.dasi.domain.xhspublish.service.bmode.intelligent.node;

import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import com.dasi.domain.xhspublish.service.bmode.context.BPublishIntelligentContext;

public abstract class AbstractBPublishIntelligentNode extends AbstractMultiThreadStrategyRouter<BPublishIntelligentContext, BPublishIntelligentContext, String> {

    @Override
    protected void multiThread(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) {
        // B 智能预处理树当前按单线串行执行。
    }

}
