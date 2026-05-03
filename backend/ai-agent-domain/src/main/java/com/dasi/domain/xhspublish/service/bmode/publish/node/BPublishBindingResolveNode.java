package com.dasi.domain.xhspublish.service.bmode.publish.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.domain.IPublishValidationDomainService;
import com.dasi.domain.xhspublish.service.support.XhsPublishBindingSupport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service("bPublishBindingResolveNode")
public class BPublishBindingResolveNode extends AbstractBPublishExecuteNode {

    @Resource
    private XhsPublishBindingSupport bindingSupport;

    @Resource
    private IPublishValidationDomainService validationDomainService;

    @Resource
    private BPublishPreflightValidateNode preflightValidateNode;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        // 第 2 步：节点自己处理 binding 解析；这里只做当前步骤需要的账号路由。
        XhsPublishTaskEntity task = dynamicContext.getTask();
        if (StringUtils.hasText(dynamicContext.getOverrideContextJson())) {
            validationDomainService.validateTaskRequest(task.getPublishType(), dynamicContext.getOverrideContextJson());
        }
        String bindingId = StringUtils.hasText(dynamicContext.getBindingId()) ? dynamicContext.getBindingId() : task.getBindingId();
        dynamicContext.setBinding(bindingSupport.resolveBinding(task.getUserId(), bindingId));
        log.info("【小红书发布】B正式发布-账号解析完成：taskId={}, bindingId={}",
                dynamicContext.getTask().getTaskId(),
                dynamicContext.getBinding().getBindingId());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return preflightValidateNode;
    }

}
