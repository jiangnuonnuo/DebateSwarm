package com.dasi.api.dto.request.xhspublish;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateXhsPublishTaskRequestDTO {

    @NotBlank
    private String taskName;

    @NotBlank
    private String publishMode;

    @NotBlank
    private String publishType;

    private String bindingId;

    private String templateId;

    private LocalDateTime scheduledPublishAt;

    @NotBlank
    private String requestJson;

    private String retryPolicyJson;

}

