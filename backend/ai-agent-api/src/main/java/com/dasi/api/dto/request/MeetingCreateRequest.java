package com.dasi.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 房间ID */
    private String roomId;

    /** 会议主题 */
    private String topic;

    /** 会议目标 */
    private String goal;

    /** 发起人ID */
    private String creatorId;

    /** 发起人角色 */
    private String creatorRole;

    /** 项目经理ID */
    private String pmId;

    /**
     * key=role, value=memberId
     */
    private Map<String, String> roleAssignments;
}
