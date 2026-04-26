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
public class XhsPublishTaskPageVO {

    private String taskId;

    private String taskName;

    private String publishMode;

    private String publishType;

    private String taskStatus;

    private String currentStage;

    private LocalDateTime scheduledPublishAt;

    private LocalDateTime updateTime;

}
