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
public class RetryXhsPublishTaskRequestDTO {

    @NotBlank
    private String taskId;

    @NotBlank
    private String triggerType;

    private String overrideContextJson;

}

