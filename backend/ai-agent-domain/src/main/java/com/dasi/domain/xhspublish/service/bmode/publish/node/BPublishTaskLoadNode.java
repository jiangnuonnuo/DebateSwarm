package com.dasi.domain.xhspublish.service.bmode.publish.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskAccessSupport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service("bPublishTaskLoadNode")
public class BPublishTaskLoadNode extends AbstractBPublishExecuteNode {

    @Resource
    private XhsPublishTaskAccessSupport taskAccessSupport;

    @Resource
    private BPublishBindingResolveNode bindingResolveNode;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        // 第 1 步：节点自己加载任务真相，后续节点统一从 context.task 读取主任务信息。
        dynamicContext.setTask(taskAccessSupport.queryOwnedTask(dynamicContext.getTaskId()));
        log.info("【小红书发布】B正式发布-任务加载完成：taskId={}", dynamicContext.getTask().getTaskId());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return bindingResolveNode;
    }

}
