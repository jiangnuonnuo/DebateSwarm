package com.dasi.domain.xhspublish.service.domain.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.service.domain.IPublishRetryDomainService;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class PublishRetryDomainServiceImpl implements IPublishRetryDomainService {

    private static final Set<String> NON_RETRYABLE_ERROR_CODES = Set.of(
            "INVALID_VISIBILITY",
            "INVALID_SCHEDULE_AT",
            "SCHEDULE_TOO_EARLY",
            "SCHEDULE_TOO_LATE",
            "TITLE_TOO_LONG",
            "IMAGE_REQUIRED"
    );

    @Override
    public boolean canRetry(String retryPolicyJson, Integer currentAttemptNo, String errorCode) {
        if (errorCode != null && NON_RETRYABLE_ERROR_CODES.contains(errorCode)) {
            return false;
        }
        if (retryPolicyJson == null || retryPolicyJson.isBlank()) {
            return true;
        }
        JSONObject policy = JSON.parseObject(retryPolicyJson);
        Integer maxRetry = firstNonNull(policy.getInteger("maxRetry"), policy.getInteger("max_retry"), 0);
        int current = currentAttemptNo == null ? 1 : currentAttemptNo;
        return current <= maxRetry + 1;
    }

    private Integer firstNonNull(Integer first, Integer second, Integer defaultValue) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        return defaultValue;
    }

}
