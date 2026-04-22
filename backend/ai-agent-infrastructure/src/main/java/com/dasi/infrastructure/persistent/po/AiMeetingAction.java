package com.dasi.infrastructure.persistent.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会议动作流水持久化对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMeetingAction {

    /** 自增主键 */
    private Long id;

    /** 动作业务ID */
    private String actionId;

    /** 会议业务ID */
    private String meetingId;

    /** 房间ID */
    private String roomId;

    /** 动作类型 */
    private String actionType;

    /** 执行人ID */
    private String actorId;

    /** 执行人角色 */
    private String actorRole;

    /** 目标ID */
    private String targetId;

    /** 目标角色 */
    private String targetRole;

    /** 动作扩展负载JSON */
    private String payload;

    /** 动作原因 */
    private String reason;

    /** 修订要求 */
    private String requiredFix;

    /** 截止时间 */
    private LocalDateTime deadline;

    /** 动作结果 */
    private String result;

    /** 对应会议版本 */
    private Integer meetingVersion;

    /** 幂等请求键 */
    private String clientRequestId;

    /** 链路追踪ID */
    private String traceId;

    /** 逻辑删除标记 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

