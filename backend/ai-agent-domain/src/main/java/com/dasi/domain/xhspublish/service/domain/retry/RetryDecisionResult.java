package com.dasi.domain.xhspublish.service.domain.retry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetryDecisionResult {

    private boolean allowed;

    private String reasonCode;

    private String reasonMessage;

    public static RetryDecisionResult allow(String reasonCode, String reasonMessage) {
        return RetryDecisionResult.builder()
                .allowed(true)
                .reasonCode(reasonCode)
                .reasonMessage(reasonMessage)
                .build();
    }

    public static RetryDecisionResult reject(String reasonCode, String reasonMessage) {
        return RetryDecisionResult.builder()
                .allowed(false)
                .reasonCode(reasonCode)
                .reasonMessage(reasonMessage)
                .build();
    }

}
