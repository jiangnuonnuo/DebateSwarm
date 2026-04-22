package com.dasi.domain.room.model.entity;

import com.dasi.domain.room.model.valobj.MeetingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingSessionEntity {

    /** 会议业务ID */
    private String meetingId;

    /** 房间ID */
    private String roomId;

    /** 会议主题 */
    private String topic;

    /** 会议目标 */
    private String goal;

    /** 会议状态 */
    private MeetingStatus status;

    /** 当前轮次 */
    private Integer roundNumber;

    /** 会议发起人ID */
    private String ownerId;

    /** 会议发起人角色 */
    private String ownerRole;

    /** 项目经理ID */
    private String pmId;

    /**
     * key=role, value=memberId
     */
    @Builder.Default
    private Map<String, String> roleAssignments = new LinkedHashMap<>();

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    public boolean isTerminal() {
        return MeetingStatus.CONCLUDED.equals(status)
                || MeetingStatus.TERMINATED.equals(status);
    }
}
