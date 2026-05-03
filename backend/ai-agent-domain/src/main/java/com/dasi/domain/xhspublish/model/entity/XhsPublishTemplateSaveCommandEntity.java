package com.dasi.domain.xhspublish.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishTemplateSaveCommandEntity {

    private String templateId;
    private String templateName;
    private String publishMode;
    private String templateConfigJson;

    @Builder.Default
    private Integer templateStatus = 1;

    @Builder.Default
    private Integer isDefault = 0;

}

