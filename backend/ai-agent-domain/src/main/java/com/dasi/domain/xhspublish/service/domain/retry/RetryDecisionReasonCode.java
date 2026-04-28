package com.dasi.domain.xhspublish.service.domain.retry;

public final class RetryDecisionReasonCode {

    public static final String ALLOW_WITHIN_POLICY = "ALLOW_WITHIN_POLICY";
    public static final String ALLOW_POLICY_NOT_SET = "ALLOW_POLICY_NOT_SET";
    public static final String REJECT_ACTIVE_ATTEMPT = "REJECT_ACTIVE_ATTEMPT";
    public static final String REJECT_NON_RETRYABLE_ERROR = "REJECT_NON_RETRYABLE_ERROR";
    public static final String REJECT_RETRY_COUNT_LIMIT = "REJECT_RETRY_COUNT_LIMIT";
    public static final String REJECT_DURATION_BUDGET_LIMIT = "REJECT_DURATION_BUDGET_LIMIT";
    public static final String REJECT_COST_BUDGET_LIMIT = "REJECT_COST_BUDGET_LIMIT";

    private RetryDecisionReasonCode() {
    }

}
