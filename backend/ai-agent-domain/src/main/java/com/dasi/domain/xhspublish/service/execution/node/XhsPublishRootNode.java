package com.dasi.domain.xhspublish.service.execution.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service("xhsPublishRootNode")
public class XhsPublishRootNode extends AbstractXhsPublishNode {

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        if (dynamicContext.getStartTime() == null) {
            dynamicContext.setStartTime(LocalDateTime.now());
        }
        log.info("【小红书发布】执行树启动：taskId={}, attemptId={}",
                dynamicContext.getTask().getTaskId(),
                dynamicContext.getAttempt().getAttemptId());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return getBean("xhsPublishGuardNode");
    }

}
