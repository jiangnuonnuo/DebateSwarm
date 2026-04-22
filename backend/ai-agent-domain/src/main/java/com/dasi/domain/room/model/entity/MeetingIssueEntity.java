package com.dasi.domain.room.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingIssueEntity {

    /** 问题单ID */
    private String issueId;

    /** 关联会议ID */
    private String meetingId;

    /** 关联房间ID */
    private String roomId;

    /** 提出问题的角色 */
    private String reporterRole;

    /** 被退回的目标角色 */
    private String assigneeRole;

    /** 退回原因 */
    private String reason;

    /** 修订要求 */
    private String requiredFix;

    /** 截止时间 */
    private LocalDateTime dueAt;

    /**
     * OPEN / RESOLVED
     */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 完成时间 */
    private LocalDateTime resolvedAt;
}
