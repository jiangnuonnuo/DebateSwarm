package com.dasi.domain.xhspublish.service.execution.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAccountBindingEntity;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.lifecycle.IXhsPublishExecutionLifecycle;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service("xhsPublishGuardNode")
public class XhsPublishGuardNode extends AbstractXhsPublishNode {

    @Resource
    private IXhsPublishExecutionLifecycle executionLifecycle;

    @Resource
    private XhsPublishPayloadNode payloadNode;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        ensureReady(dynamicContext);

        executionLifecycle.markExecutionStarted(dynamicContext, dynamicContext.getStartTime());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return payloadNode;
    }

    private void ensureReady(XhsPublishExecutionContext executionContext) {
        if (executionContext == null) {
            throw new WorkException("发布执行上下文不能为空");
        }
        if (executionContext.getTask() == null) {
            throw new WorkException("发布任务不能为空");
        }
        if (executionContext.getAttempt() == null) {
            throw new WorkException("发布尝试不能为空");
        }
        XhsPublishAccountBindingEntity binding = executionContext.getBinding();
        if (binding == null) {
            throw new WorkException("发布账号绑定不能为空");
        }
        if (!StringUtils.hasText(binding.getMcpTenantId()) || !StringUtils.hasText(binding.getMcpAccountId())) {
            throw new WorkException("发布账号绑定缺少 MCP 路由信息");
        }
        if (executionContext.getSourceContext() == null) {
            throw new WorkException("发布上下文不能为空");
        }
    }

}
