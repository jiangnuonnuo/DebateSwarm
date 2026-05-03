package com.dasi.infrastructure.dao.po.xhspublish;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** 小红书发布任务持久化对象 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiXhsPublishTask {

    private Long id;

    private String taskId;

    private Long userId;

    private String taskName;

    private String publishMode;

    private String publishType;

    private String taskStatus;

    private String currentStage;

    private String bindingId;

    private String templateId;

    private LocalDateTime scheduledPublishAt;

    private String requestJson;

    private String latestContextJson;

    private String finalResultJson;

    private String retryPolicyJson;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

