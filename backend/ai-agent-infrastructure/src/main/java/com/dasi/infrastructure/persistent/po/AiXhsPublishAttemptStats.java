package com.dasi.infrastructure.persistent.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiXhsPublishAttemptStats {

    private Integer attemptCount;

    private Integer maxAttemptNo;

    private Long totalDurationMs;

    private BigDecimal totalCostAmount;

}
