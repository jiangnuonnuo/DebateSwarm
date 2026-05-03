package com.dasi.api.dto.response.xhspublish;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishReviewResponseDTO {

    private String reviewId;

    private String reviewStage;

    private String reviewMode;

    private String reviewStatus;

    private String reviewComment;

    private LocalDateTime decidedTime;

}

