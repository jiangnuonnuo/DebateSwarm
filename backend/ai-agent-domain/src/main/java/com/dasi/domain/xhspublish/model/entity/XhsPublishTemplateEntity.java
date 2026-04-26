package com.dasi.domain.xhspublish.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** 小红书发布模板领域实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishTemplateEntity {

    private Long id;

    private String templateId;

    private Long userId;

    private String templateName;

    private String publishMode;

    private String templateConfigJson;

    private Integer templateStatus;

    private Integer isDefault;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
