package com.dasi.domain.xhspublish.service.strategy;

import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;

public interface IXhsPublishExecuteStrategy {

    String getKey();

    void execute(XhsPublishExecutionContext executionContext);

}
