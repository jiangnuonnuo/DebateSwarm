package com.dasi.domain.xhspublish.service.domain.retry.guard;

import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionContext;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionReasonCode;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(40)
@Component
public class DurationBudgetGuard implements IRetryDecisionGuard {

    @Override
    public RetryDecisionResult decide(RetryDecisionContext context) {
        if (context == null || context.getMaxTotalDurationMs() == null) {
            return null;
        }
        long usedDurationMs = context.getTotalDurationMs() == null ? 0L : context.getTotalDurationMs();
        if (usedDurationMs >= context.getMaxTotalDurationMs()) {
            return RetryDecisionResult.reject(RetryDecisionReasonCode.REJECT_DURATION_BUDGET_LIMIT,
                    "重试总耗时超限，usedDurationMs=" + usedDurationMs + ", maxTotalDurationMs=" + context.getMaxTotalDurationMs());
        }
        return null;
    }

}
