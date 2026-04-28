package com.dasi.domain.xhspublish.service.domain.impl;

import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionContext;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionReasonCode;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;
import com.dasi.domain.xhspublish.service.domain.retry.guard.ActiveAttemptGuard;
import com.dasi.domain.xhspublish.service.domain.retry.guard.CostBudgetGuard;
import com.dasi.domain.xhspublish.service.domain.retry.guard.DurationBudgetGuard;
import com.dasi.domain.xhspublish.service.domain.retry.guard.ErrorCodeGuard;
import com.dasi.domain.xhspublish.service.domain.retry.guard.RetryCountGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishRetryDomainServiceImplTest {

    private PublishRetryDomainServiceImpl retryDomainService;

    @BeforeEach
    void setUp() {
        retryDomainService = new PublishRetryDomainServiceImpl();
        ReflectionTestUtils.setField(retryDomainService, "retryDecisionGuards", List.of(
                new ActiveAttemptGuard(),
                new ErrorCodeGuard(),
                new RetryCountGuard(),
                new DurationBudgetGuard(),
                new CostBudgetGuard()
        ));
    }

    @Test
    void shouldRejectWhenActiveAttemptExists() {
        RetryDecisionResult result = retryDomainService.evaluate(RetryDecisionContext.builder()
                .activeAttempt(true)
                .retryPolicyJson("{\"maxRetry\":3}")
                .build());

        assertFalse(result.isAllowed());
        assertEquals(RetryDecisionReasonCode.REJECT_ACTIVE_ATTEMPT, result.getReasonCode());
    }

    @Test
    void shouldRejectNonRetryableErrorCode() {
        RetryDecisionResult result = retryDomainService.evaluate(RetryDecisionContext.builder()
                .activeAttempt(false)
                .retryPolicyJson("{\"maxRetry\":3}")
                .latestAttemptNo(1)
                .latestErrorCode("TITLE_TOO_LONG")
                .totalDurationMs(100L)
                .totalCostAmount(BigDecimal.ZERO)
                .build());

        assertFalse(result.isAllowed());
        assertEquals(RetryDecisionReasonCode.REJECT_NON_RETRYABLE_ERROR, result.getReasonCode());
    }

    @Test
    void shouldRejectWhenRetryCountReached() {
        RetryDecisionResult result = retryDomainService.evaluate(RetryDecisionContext.builder()
                .activeAttempt(false)
                .retryPolicyJson("{\"maxRetry\":2}")
                .latestAttemptNo(3)
                .latestErrorCode("MCP_TRANSIENT_FAIL")
                .totalDurationMs(100L)
                .totalCostAmount(BigDecimal.ZERO)
                .build());

        assertFalse(result.isAllowed());
        assertEquals(RetryDecisionReasonCode.REJECT_RETRY_COUNT_LIMIT, result.getReasonCode());
    }

    @Test
    void shouldRejectWhenDurationOrCostExceeded() {
        RetryDecisionResult durationResult = retryDomainService.evaluate(RetryDecisionContext.builder()
                .activeAttempt(false)
                .retryPolicyJson("{\"maxRetry\":3,\"max_total_duration_ms\":1000}")
                .latestAttemptNo(2)
                .latestErrorCode("MCP_TRANSIENT_FAIL")
                .totalDurationMs(1000L)
                .totalCostAmount(BigDecimal.ZERO)
                .build());
        assertFalse(durationResult.isAllowed());
        assertEquals(RetryDecisionReasonCode.REJECT_DURATION_BUDGET_LIMIT, durationResult.getReasonCode());

        RetryDecisionResult costResult = retryDomainService.evaluate(RetryDecisionContext.builder()
                .activeAttempt(false)
                .retryPolicyJson("{\"maxRetry\":3,\"max_cost_budget\":1.5}")
                .latestAttemptNo(2)
                .latestErrorCode("MCP_TRANSIENT_FAIL")
                .totalDurationMs(100L)
                .totalCostAmount(new BigDecimal("1.5"))
                .build());
        assertFalse(costResult.isAllowed());
        assertEquals(RetryDecisionReasonCode.REJECT_COST_BUDGET_LIMIT, costResult.getReasonCode());
    }

    @Test
    void shouldAllowRetryWhenWithinPolicy() {
        RetryDecisionResult result = retryDomainService.evaluate(RetryDecisionContext.builder()
                .activeAttempt(false)
                .retryPolicyJson("{\"maxRetry\":3,\"max_total_duration_ms\":5000,\"max_cost_budget\":3.2}")
                .latestAttemptNo(2)
                .latestErrorCode("MCP_TRANSIENT_FAIL")
                .totalDurationMs(1200L)
                .totalCostAmount(new BigDecimal("1.7"))
                .build());

        assertTrue(result.isAllowed());
        assertEquals(RetryDecisionReasonCode.ALLOW_WITHIN_POLICY, result.getReasonCode());
    }

}
