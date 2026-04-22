package com.dasi.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会议ID */
    private String meetingId;

    /** 房间ID */
    private String roomId;

    /** 会议主题 */
    private String topic;

    /** 会议目标 */
    private String goal;

    /** 会议状态码 */
    private String status;

    /** 当前轮次 */
    private Integer roundNumber;

    /** 当前会议版本 */
    private Integer version;

    /** 发起人ID */
    private String ownerId;

    /** 发起人角色 */
    private String ownerRole;

    /** 项目经理ID */
    private String pmId;

    /** 角色绑定映射 */
    private Map<String, String> roleAssignments;

    /** 阶段总结 */
    private MeetingSummaryDTO summary;

    /** 当前开放问题单 */
    private List<MeetingIssueDTO> openIssues;

    /** 时间线动作数量 */
    private Integer timelineSize;
}
