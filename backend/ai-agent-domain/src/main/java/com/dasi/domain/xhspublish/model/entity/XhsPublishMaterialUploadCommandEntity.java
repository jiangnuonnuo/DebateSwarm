package com.dasi.domain.xhspublish.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishMaterialUploadCommandEntity {
    private String taskId;

    private String attemptId;

    @Builder.Default
    private String assetType = "image";
    private String sourceType;

    private String originUrl;

    @Builder.Default
    private Integer sortNo = 1;

}

