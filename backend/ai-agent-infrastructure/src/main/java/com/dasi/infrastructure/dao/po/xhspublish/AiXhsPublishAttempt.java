package com.dasi.infrastructure.dao.po.xhspublish;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 小红书发布尝试持久化对象 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiXhsPublishAttempt {

    private Long id;

    private String attemptId;

    private String taskId;

    private Integer attemptNo;

    private String triggerType;

    private String attemptStatus;

    private String stage;

    private String contextJson;

    private String publishRequestJson;

    private String publishResultJson;

    private String errorCode;

    private String errorMessage;

    private Integer retryable;

    private BigDecimal costAmount;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMs;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

