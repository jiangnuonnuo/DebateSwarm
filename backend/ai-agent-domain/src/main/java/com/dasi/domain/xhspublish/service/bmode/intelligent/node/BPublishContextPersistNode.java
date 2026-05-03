package com.dasi.domain.xhspublish.service.bmode.intelligent.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.model.entity.XhsPublishUpdateContextCommandEntity;
import com.dasi.domain.xhspublish.service.bmode.context.BPublishIntelligentContext;
import com.dasi.domain.xhspublish.service.task.XhsPublishTaskCommandDomainService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("bPublishContextPersistNode")
public class BPublishContextPersistNode extends AbstractBPublishIntelligentNode {

    @Resource
    private XhsPublishTaskCommandDomainService taskCommandDomainService;

    @Resource
    private BPublishSubmitDelegateNode submitDelegateNode;

    @Override
    protected String doApply(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) throws Exception {
        // 第 7 步：把智能预处理阶段产出的 latestContextJson 回写到任务主表，供正式发布树直接消费。
        String taskId = dynamicContext.getTaskId();
        String latestContextJson = dynamicContext.getLatestContextJson();

        XhsPublishUpdateContextCommandEntity command = XhsPublishUpdateContextCommandEntity.builder()
                .taskId(taskId)
                .latestContextJson(latestContextJson)
                .build();

        taskCommandDomainService.updateTaskContext(command);
        log.info("【小红书发布】B智能预处理-上下文落库完成：taskId={}", taskId);
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<BPublishIntelligentContext, BPublishIntelligentContext, String> get(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) {
        return submitDelegateNode;
    }

}
