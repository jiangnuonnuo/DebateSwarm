package com.dasi.domain.xhspublish.service.domain.retry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryDecisionContext {

    private String retryPolicyJson;

    private Integer latestAttemptNo;

    private String latestErrorCode;

    private Long totalDurationMs;

    private BigDecimal totalCostAmount;

    private Boolean activeAttempt;

    private Integer maxRetry;

    private Long maxTotalDurationMs;

    private BigDecimal maxCostBudget;

}
