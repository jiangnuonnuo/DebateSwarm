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
public class SubmitXhsPublishTaskDTO {

    @NotBlank
    private String taskId;

    private String bindingId;

    private String overrideContextJson;

    @Builder.Default
    private String triggerType = "submit";

}
