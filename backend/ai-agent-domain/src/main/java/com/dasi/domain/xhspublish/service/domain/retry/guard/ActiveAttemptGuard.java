package com.dasi.domain.xhspublish.service.domain.retry.guard;

import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionContext;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionReasonCode;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(10)
@Component
public class ActiveAttemptGuard implements IRetryDecisionGuard {

    @Override
    public RetryDecisionResult decide(RetryDecisionContext context) {
        if (context == null) {
            return RetryDecisionResult.reject(RetryDecisionReasonCode.REJECT_ACTIVE_ATTEMPT, "重试上下文为空");
        }
        if (Boolean.TRUE.equals(context.getActiveAttempt())) {
            return RetryDecisionResult.reject(RetryDecisionReasonCode.REJECT_ACTIVE_ATTEMPT, "存在活跃执行中的 attempt");
        }
        return null;
    }

}
