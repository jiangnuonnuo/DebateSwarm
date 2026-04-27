package com.dasi.domain.xhspublish.service.execution.guard;

import com.dasi.domain.xhspublish.model.entity.XhsPublishAccountBindingEntity;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.types.exception.WorkException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PublishExecutionBindingGuard implements IXhsPublishExecutionGuard {

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public void guard(XhsPublishExecutionContext executionContext) {
        XhsPublishAccountBindingEntity binding = executionContext.getBinding();
        if (binding == null) {
            throw new WorkException("发布账号绑定不能为空");
        }
        if (!StringUtils.hasText(binding.getMcpTenantId()) || !StringUtils.hasText(binding.getMcpAccountId())) {
            throw new WorkException("发布账号绑定缺少 MCP 路由信息");
        }
    }

}
