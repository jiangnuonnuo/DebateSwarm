package com.dasi.domain.xhspublish.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishTemplateVO {

    private String templateId;

    private String templateName;

    private String publishMode;

    private Integer templateStatus;

    private Integer isDefault;

    private LocalDateTime updateTime;

}
