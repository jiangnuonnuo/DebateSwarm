package com.dasi.api.dto.response.xhspublish;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishTaskDetailResponseDTO {

    private String taskId;

    private String taskName;

    private String publishMode;

    private String publishType;

    private String taskStatus;

    private String currentStage;

    private String bindingId;

    private String templateId;

    private LocalDateTime scheduledPublishAt;

    private String requestJson;

    private String latestContextJson;

    private String finalResultJson;

    private String retryPolicyJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private List<XhsPublishAttemptResponseDTO> attemptList;

    private List<XhsPublishReviewResponseDTO> reviewList;

    private List<XhsPublishAssetResponseDTO> assetList;

}

