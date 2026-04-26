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
public class UploadXhsPublishMaterialDTO {

    @NotBlank
    private String taskId;

    private String attemptId;

    @Builder.Default
    private String assetType = "image";

    @NotBlank
    private String sourceType;

    private String originUrl;

    @Builder.Default
    private Integer sortNo = 1;

}
