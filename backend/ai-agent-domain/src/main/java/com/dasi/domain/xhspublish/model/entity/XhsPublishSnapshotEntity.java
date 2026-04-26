package com.dasi.domain.xhspublish.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** 小红书发布快照领域实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishSnapshotEntity {

    private Long id;

    private String snapshotId;

    private String taskId;

    private String attemptId;

    private Integer roundNo;

    private String stage;

    private String snapshotPath;

    private String cleanupStatus;

    private LocalDateTime expireTime;

    private LocalDateTime deletedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
