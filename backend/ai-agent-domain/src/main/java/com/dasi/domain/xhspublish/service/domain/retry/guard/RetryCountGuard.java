package com.dasi.domain.xhspublish.service.domain.retry.guard;

import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionContext;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionReasonCode;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(30)
@Component
public class RetryCountGuard implements IRetryDecisionGuard {

    @Override
    public RetryDecisionResult decide(RetryDecisionContext context) {
        if (context == null || context.getMaxRetry() == null) {
            return null;
        }
        int latestAttemptNo = context.getLatestAttemptNo() == null ? 1 : context.getLatestAttemptNo();
        int usedRetryCount = Math.max(0, latestAttemptNo - 1);
        if (usedRetryCount >= context.getMaxRetry()) {
            return RetryDecisionResult.reject(RetryDecisionReasonCode.REJECT_RETRY_COUNT_LIMIT,
                    "重试次数超限，usedRetryCount=" + usedRetryCount + ", maxRetry=" + context.getMaxRetry());
        }
        return null;
    }

}
