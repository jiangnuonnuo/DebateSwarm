package com.dasi.domain.room.model.entity;

import com.dasi.domain.room.model.valobj.MeetingActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingActionEntity {

    /** 动作ID */
    private String actionId;

    /** 关联会议ID */
    private String meetingId;

    /** 关联房间ID */
    private String roomId;

    /** 动作类型 */
    private MeetingActionType actionType;

    /** 执行人ID */
    private String actorId;

    /** 执行人角色 */
    private String actorRole;

    /** 动作目标ID */
    private String targetId;

    /** 动作目标角色 */
    private String targetRole;

    /** 动作扩展负载(JSON字符串) */
    private String payload;

    /** 动作原因 */
    private String reason;

    /** 修订要求 */
    private String requiredFix;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** 动作结果 */
    private String result;

    /** 对应会议版本号 */
    private Integer meetingVersion;

    /** 幂等请求键，同 meetingId 下唯一 */
    private String clientRequestId;

    /** 链路追踪ID */
    private String traceId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
