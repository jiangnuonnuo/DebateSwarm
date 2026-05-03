package com.dasi.api.dto.request.xhspublish;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewXhsPublishTaskRequestDTO {

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

