package com.dasi.domain.xhspublish.service.domain.retry.guard;

import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionContext;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;

public interface IRetryDecisionGuard {

    RetryDecisionResult decide(RetryDecisionContext context);

}
