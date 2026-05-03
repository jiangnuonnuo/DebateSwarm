package com.dasi.domain.xhspublish.service.bmode.intelligent.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.model.entity.XhsPublishSubmitTaskCommandEntity;
import com.dasi.domain.xhspublish.service.bmode.context.BPublishIntelligentContext;
import com.dasi.domain.xhspublish.service.task.XhsPublishTaskCommandDomainService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("bPublishSubmitDelegateNode")
public class BPublishSubmitDelegateNode extends AbstractBPublishIntelligentNode {

    @Resource
    private XhsPublishTaskCommandDomainService taskCommandDomainService;

    @Override
    protected String doApply(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) throws Exception {
        // 第 8 步：智能预处理树只负责准备上下文，真正发布统一委托给正式发布树处理。
        String taskId = dynamicContext.getTaskId();
        String triggerType = "intelligent_submit";

        XhsPublishSubmitTaskCommandEntity command = XhsPublishSubmitTaskCommandEntity.builder()
                .taskId(taskId)
                .triggerType(triggerType)
                .build();

        String attemptId = taskCommandDomainService.submitTask(command);
        dynamicContext.setAttemptId(attemptId);
        log.info("【小红书发布】B智能预处理-已委托正式发布：taskId={}, attemptId={}",
                taskId, attemptId);
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<BPublishIntelligentContext, BPublishIntelligentContext, String> get(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) {
        return defaultStrategyHandler;
    }

}
