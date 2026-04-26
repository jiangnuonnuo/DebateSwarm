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
public class SaveXhsPublishTemplateDTO {

    private String templateId;

    @NotBlank
    private String templateName;

    @NotBlank
    private String publishMode;

    @NotBlank
    private String templateConfigJson;

    @Builder.Default
    private Integer templateStatus = 1;

    @Builder.Default
    private Integer isDefault = 0;

}
