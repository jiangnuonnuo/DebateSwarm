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
public class SubmitXhsPublishTaskRequestDTO {

    @NotBlank
    private String taskId;

    private String bindingId;

    private String overrideContextJson;

    @Builder.Default
    private String triggerType = "submit";

}

