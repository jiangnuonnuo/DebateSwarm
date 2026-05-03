package com.dasi.domain.xhspublish.service.bmode.intelligent.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.bmode.context.BPublishIntelligentContext;
import com.dasi.domain.xhspublish.service.generator.IXhsPublishGeneratorService;
import com.dasi.domain.xhspublish.service.generator.GeneratedPublishContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("bPublishCopyGenerateNode")
public class BPublishCopyGenerateNode extends AbstractBPublishIntelligentNode {

    @Resource
    private IXhsPublishGeneratorService generatorService;

    @Resource
    private BPublishContentNormalizeNode contentNormalizeNode;

    @Override
    protected String doApply(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) throws Exception {

        GeneratedPublishContext content = generatorService.generate(dynamicContext.getCommand(), dynamicContext.getSelectedAssetIds().size());
        dynamicContext.setGeneratedPublishContext(content);

        log.info("【小红书发布】B智能预处理-文案生成完成：taskId={}, titleLength={}",
                dynamicContext.getTaskId(),
                dynamicContext.getGeneratedPublishContext() == null || dynamicContext.getGeneratedPublishContext().getTitle() == null
                        ? 0 : dynamicContext.getGeneratedPublishContext().getTitle().length());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<BPublishIntelligentContext, BPublishIntelligentContext, String> get(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) {
        return contentNormalizeNode;
    }

}
