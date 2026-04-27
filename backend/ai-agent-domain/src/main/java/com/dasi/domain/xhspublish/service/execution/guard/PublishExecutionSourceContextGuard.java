package com.dasi.domain.xhspublish.service.execution.guard;

import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.types.exception.WorkException;
import org.springframework.stereotype.Component;

@Component
public class PublishExecutionSourceContextGuard implements IXhsPublishExecutionGuard {

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public void guard(XhsPublishExecutionContext executionContext) {
        if (executionContext.getSourceContext() == null) {
            throw new WorkException("发布上下文不能为空");
        }
    }

}
