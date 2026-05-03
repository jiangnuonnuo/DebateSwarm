package com.dasi.domain.xhspublish.service.bmode.publish.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service("bPublishExecuteRootNode")
public class BPublishExecuteRootNode extends AbstractBPublishExecuteNode {

    @Resource
    private BPublishTaskLoadNode taskLoadNode;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        // B 正式发布树入口：统一设置开始时间并记录一次树启动日志。
        if (dynamicContext.getStartTime() == null) {
            dynamicContext.setStartTime(LocalDateTime.now());
        }
        log.info("【小红书发布】B正式发布树启动：taskId={}, retryMode={}", dynamicContext.getTaskId(), dynamicContext.isRetryMode());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return taskLoadNode;
    }

}
