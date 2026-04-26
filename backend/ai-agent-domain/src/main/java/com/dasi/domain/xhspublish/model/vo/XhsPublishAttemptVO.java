package com.dasi.domain.xhspublish.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishAttemptVO {

    private String attemptId;

    private Integer attemptNo;

    private String attemptStatus;

    private String stage;

    private String errorCode;

    private String errorMessage;

    private BigDecimal costAmount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

}
