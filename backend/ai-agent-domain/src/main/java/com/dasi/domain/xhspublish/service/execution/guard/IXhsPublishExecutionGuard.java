package com.dasi.domain.xhspublish.service.execution.guard;

import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;

public interface IXhsPublishExecutionGuard {

    int getOrder();

    void guard(XhsPublishExecutionContext executionContext);

}
