package com.dasi.domain.xhspublish.service.domain;

import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionContext;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;

public interface IPublishRetryDomainService {

    RetryDecisionResult evaluate(RetryDecisionContext context);

}
