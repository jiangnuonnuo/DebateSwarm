package com.dasi.domain.xhspublish.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewXhsPublishTaskDTO {

    @NotBlank
    private String taskId;

    private String attemptId;

    @NotBlank
    private String reviewStage;

    @NotBlank
    private String reviewMode;

    @NotBlank
    private String reviewStatus;

    private String reviewComment;

    private String reviewPayloadJson;

    private String decisionJson;

}
