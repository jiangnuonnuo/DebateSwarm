package com.dasi.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingIssueDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 问题单ID */
    private String issueId;

    /** 提出问题角色 */
    private String reporterRole;

    /** 被退回角色 */
    private String assigneeRole;

    /** 退回原因 */
    private String reason;

    /** 修订要求 */
    private String requiredFix;

    /** 截止时间 */
    private String dueAt;

    /** 问题状态 */
    private String status;

    /** 创建时间 */
    private String createdAt;

    /** 解决时间 */
    private String resolvedAt;
}
