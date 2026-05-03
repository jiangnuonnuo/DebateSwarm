package com.dasi.domain.xhspublish.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishReviewCommandEntity {
    private String taskId;

    private String attemptId;
    private String reviewStage;
    private String reviewMode;
    private String reviewStatus;

    private String reviewComment;

    private String reviewPayloadJson;

    private String decisionJson;

}

