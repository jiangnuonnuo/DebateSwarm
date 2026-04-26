package com.dasi.domain.xhspublish.service.rule;

import com.dasi.types.exception.WorkException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PublishBindingPayloadRule implements IXhsPublishPayloadRule {

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public void apply(XhsPublishRuleContext context) {
        if (context.getBinding() == null) {
            throw new WorkException("发布账号绑定不能为空");
        }
        if (!StringUtils.hasText(context.getBinding().getMcpTenantId()) || !StringUtils.hasText(context.getBinding().getMcpAccountId())) {
            throw new WorkException("发布账号绑定缺少 MCP 路由信息");
        }
        context.getPublishPayload().put("tenant_id", context.getBinding().getMcpTenantId());
        context.getPublishPayload().put("account_id", context.getBinding().getMcpAccountId());
        context.getPublishPayload().put("task_id", context.getTask().getTaskId());
        context.getPublishPayload().put("mode", "async");
    }

}
