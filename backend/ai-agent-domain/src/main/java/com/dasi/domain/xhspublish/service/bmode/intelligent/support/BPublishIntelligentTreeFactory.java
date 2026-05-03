package com.dasi.domain.xhspublish.service.bmode.intelligent.support;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.bmode.context.BPublishIntelligentContext;
import com.dasi.domain.xhspublish.service.bmode.intelligent.node.BPublishIntelligentRootNode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class BPublishIntelligentTreeFactory {

    @Resource
    private BPublishIntelligentRootNode rootNode;

    public StrategyHandler<BPublishIntelligentContext, BPublishIntelligentContext, String> getRootNode() {
        // 智能预处理树固定链路：
        // Root -> ClientCheck -> InputNormalize -> DraftCreate -> MaterialRegister
        // -> CopyGenerate -> ContentNormalize -> ContextPersist -> SubmitDelegate
        return rootNode;
    }

}
