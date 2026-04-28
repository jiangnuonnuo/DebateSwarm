package com.dasi.domain.xhspublish.service.domain.retry.guard;

import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionContext;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionReasonCode;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

@Order(20)
@Component
public class ErrorCodeGuard implements IRetryDecisionGuard {

    private static final Set<String> NON_RETRYABLE_ERROR_CODES = Set.of(
            "INVALID_VISIBILITY",
            "INVALID_SCHEDULE_AT",
            "SCHEDULE_TOO_EARLY",
            "SCHEDULE_TOO_LATE",
            "TITLE_TOO_LONG",
            "IMAGE_REQUIRED",
            "VALIDATION_ERROR"
    );

    @Override
    public RetryDecisionResult decide(RetryDecisionContext context) {
        String errorCode = context == null ? null : context.getLatestErrorCode();
        if (!StringUtils.hasText(errorCode)) {
            return null;
        }
        if (NON_RETRYABLE_ERROR_CODES.contains(errorCode)) {
            return RetryDecisionResult.reject(RetryDecisionReasonCode.REJECT_NON_RETRYABLE_ERROR, "错误码不允许重试：" + errorCode);
        }
        return null;
    }

}
