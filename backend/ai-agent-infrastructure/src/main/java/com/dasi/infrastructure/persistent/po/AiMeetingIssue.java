package com.dasi.infrastructure.persistent.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会议问题单持久化对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMeetingIssue {

    /** 自增主键 */
    private Long id;

    /** 问题单业务ID */
    private String issueId;

    /** 会议业务ID */
    private String meetingId;

    /** 房间ID */
    private String roomId;

    /** 提出问题角色 */
    private String reporterRole;

    /** 被退回角色 */
    private String assigneeRole;

    /** 退回原因 */
    private String reason;

    /** 修订要求 */
    private String requiredFix;

    /** 截止时间 */
    private LocalDateTime dueAt;

    /** 问题状态 OPEN/RESOLVED */
    private String status;

    /** 解决时间 */
    private LocalDateTime resolvedAt;

    /** 逻辑删除标记 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

