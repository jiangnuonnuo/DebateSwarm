package com.dasi.infrastructure.dao.po.xhspublish;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** 小红书发布模板持久化对象 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiXhsPublishTemplate {

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

