package com.dasi.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingStartRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 房间ID */
    private String roomId;

    /** 会议ID */
    private String meetingId;

    /** 动作执行人ID */
    private String actorId;

    /** 动作执行人角色 */
    private String actorRole;

    /** 期望版本号，用于并发控制 */
    private Integer expectedVersion;
}
