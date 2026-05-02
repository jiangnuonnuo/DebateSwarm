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
        // 步骤 1：发布 payload 基础元信息始终保留，便于远端异步任务和日志定位。
        context.getPublishPayload().put("task_id", context.getTask().getTaskId());
        context.getPublishPayload().put("mode", "async");

        // 步骤 2：若智能发布未显式选择 bindingId，则不覆盖 tenant/account，交给 MCP 服务端默认账号处理。
        if (useMcpDefaultAccount(context)) {
            return;
        }

        // 步骤 3：显式绑定账号时，必须把 tenant/account 路由写入 payload，确保发布到指定账号。
        if (context.getBinding() == null) {
            throw new WorkException("发布账号绑定不能为空");
        }
        if (!StringUtils.hasText(context.getBinding().getMcpTenantId()) || !StringUtils.hasText(context.getBinding().getMcpAccountId())) {
            throw new WorkException("发布账号绑定缺少 MCP 路由信息");
        }
        context.getPublishPayload().put("tenant_id", context.getBinding().getMcpTenantId());
        context.getPublishPayload().put("account_id", context.getBinding().getMcpAccountId());
    }

    private boolean useMcpDefaultAccount(XhsPublishRuleContext context) {
        if (context == null || context.getSourceContext() == null) {
            return false;
        }
        var ext = context.getSourceContext().getJSONObject("ext");
        return ext != null && ext.getBooleanValue("useMcpDefaultAccount");
    }

}
