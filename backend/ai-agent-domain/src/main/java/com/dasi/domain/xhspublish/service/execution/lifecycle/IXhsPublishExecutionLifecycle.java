package com.dasi.domain.xhspublish.service.execution.lifecycle;

import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;

import java.time.LocalDateTime;

public interface IXhsPublishExecutionLifecycle {

    void markExecutionStarted(XhsPublishExecutionContext executionContext, LocalDateTime startTime);

    void markPayloadBuilt(XhsPublishExecutionContext executionContext, String publishRequestJson);

    void markPublishSubmitted(XhsPublishExecutionContext executionContext);

    void markPublishAccepted(XhsPublishExecutionContext executionContext, XhsPublishRemoteResult remoteResult, LocalDateTime startTime);

    void markPublishSucceeded(XhsPublishExecutionContext executionContext, XhsPublishRemoteResult remoteResult, LocalDateTime startTime);

    void markPublishFailed(XhsPublishExecutionContext executionContext, XhsPublishRemoteResult remoteResult, LocalDateTime startTime);

    void markExecutionException(XhsPublishExecutionContext executionContext, Exception exception, LocalDateTime startTime);

}
