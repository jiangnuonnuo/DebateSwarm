package com.dasi.domain.xhspublish.service.domain;

public interface IPublishRetryDomainService {

    boolean canRetry(String retryPolicyJson, Integer currentAttemptNo, String errorCode);

}
