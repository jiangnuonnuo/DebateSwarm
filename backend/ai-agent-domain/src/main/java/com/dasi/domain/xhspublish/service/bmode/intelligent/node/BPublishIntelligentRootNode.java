package com.dasi.domain.xhspublish.service.bmode.intelligent.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.bmode.context.BPublishIntelligentContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("bPublishIntelligentRootNode")
public class BPublishIntelligentRootNode extends AbstractBPublishIntelligentNode {

    @Resource
    private BPublishClientCheckNode clientCheckNode;

    @Override
    protected String doApply(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) throws Exception {
        // 智能预处理树入口：记录启动日志并把流转交给 client 校验节点。
        log.info("【小红书发布】B智能预处理树启动：taskName={}, clientId={}",
                dynamicContext.getCommand().getTaskName(),
                dynamicContext.getCommand().getClientId());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<BPublishIntelligentContext, BPublishIntelligentContext, String> get(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) {
        return clientCheckNode;
    }

}
