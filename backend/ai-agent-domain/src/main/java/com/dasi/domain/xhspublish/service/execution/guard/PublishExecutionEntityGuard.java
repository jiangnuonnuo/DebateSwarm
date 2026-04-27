package com.dasi.domain.xhspublish.service.execution.guard;

import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.types.exception.WorkException;
import org.springframework.stereotype.Component;

@Component
public class PublishExecutionEntityGuard implements IXhsPublishExecutionGuard {

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public void guard(XhsPublishExecutionContext executionContext) {
        if (executionContext == null) {
            throw new WorkException("发布执行上下文不能为空");
        }
        if (executionContext.getTask() == null) {
            throw new WorkException("发布任务不能为空");
        }
        if (executionContext.getAttempt() == null) {
            throw new WorkException("发布尝试不能为空");
        }
    }

}
