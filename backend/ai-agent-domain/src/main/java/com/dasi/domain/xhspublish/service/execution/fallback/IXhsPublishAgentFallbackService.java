package com.dasi.domain.xhspublish.service.execution.fallback;

import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;

public interface IXhsPublishAgentFallbackService {

    XhsPublishRemoteResult execute(XhsPublishExecutionContext executionContext);

}
