package com.dasi.domain.xhspublish.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** 小红书发布审核领域实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishReviewEntity {

    private Long id;

    private String reviewId;

    private String taskId;

    private String attemptId;

    private String reviewStage;

    private String reviewMode;

    private String reviewStatus;

    private Long reviewerId;

    private String reviewComment;

    private String reviewPayloadJson;

    private String decisionJson;

    private LocalDateTime decidedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
