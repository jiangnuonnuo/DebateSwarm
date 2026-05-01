package com.dasi.domain.xhspublish.service.support;

import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionReasonCode;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryDecisionMessageSupportTest {

    @Test
    void shouldMapKnownReasonCodeToUserMessage() {
        RetryDecisionResult result = RetryDecisionResult.reject(
                RetryDecisionReasonCode.REJECT_RETRY_COUNT_LIMIT,
                "raw-reason"
        );

        String message = RetryDecisionMessageSupport.toUserMessage(result);
        assertTrue(message.contains("已达到最大重试次数"));
        assertTrue(message.contains(RetryDecisionReasonCode.REJECT_RETRY_COUNT_LIMIT));
    }

    @Test
    void shouldFallbackToReasonMessageWhenReasonCodeUnknown() {
        RetryDecisionResult result = RetryDecisionResult.reject("UNKNOWN_CODE", "fallback-message");
        assertEquals("fallback-message（UNKNOWN_CODE）", RetryDecisionMessageSupport.toUserMessage(result));
    }

}
