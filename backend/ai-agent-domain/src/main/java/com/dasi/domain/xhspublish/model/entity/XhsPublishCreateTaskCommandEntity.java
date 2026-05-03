package com.dasi.domain.xhspublish.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishCreateTaskCommandEntity {
    private String taskName;
    private String publishMode;
    private String publishType;

    private String bindingId;

    private String templateId;

    private LocalDateTime scheduledPublishAt;
    private String requestJson;

    private String retryPolicyJson;

}

