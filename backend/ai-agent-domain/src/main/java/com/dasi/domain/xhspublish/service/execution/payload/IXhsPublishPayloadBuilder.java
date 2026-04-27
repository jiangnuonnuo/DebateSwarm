package com.dasi.domain.xhspublish.service.execution.payload;

import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;

public interface IXhsPublishPayloadBuilder {

    String build(XhsPublishExecutionContext executionContext);

}
