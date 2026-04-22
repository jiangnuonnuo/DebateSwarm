package com.dasi.infrastructure.persistent.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会议主表持久化对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMeetingSession {

    /** 自增主键 */
    private Long id;

    /** 会议业务ID */
    private String meetingId;

    /** 房间ID */
    private String roomId;

    /** 会议主题 */
    private String topic;

    /** 会议目标 */
    private String goal;

    /** 会议状态 */
    private String status;

    /** 当前轮次 */
    private Integer roundNumber;

    /** 发起人ID */
    private String ownerId;

    /** 发起人角色 */
    private String ownerRole;

    /** 项目经理ID */
    private String pmId;

    /** 角色绑定JSON */
    private String roleAssignmentsJson;

    /** 乐观锁版本 */
    private Integer version;

    /** 逻辑删除标记 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

