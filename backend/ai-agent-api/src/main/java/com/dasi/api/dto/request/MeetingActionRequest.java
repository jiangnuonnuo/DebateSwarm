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
public class MeetingActionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 房间ID */
    private String roomId;

    /** 会议ID */
    private String meetingId;

    /** 动作类型：REPORT/RETURN/APPROVE/QUESTION/ASSIGN/DECISION/RESUME/CONCLUDE/TERMINATE */
    private String actionType;

    /** 执行动作的成员ID */
    private String actorId;

    /** 执行动作的角色 */
    private String actorRole;

    /** 动作目标成员ID */
    private String targetId;

    /** 动作目标角色 */
    private String targetRole;

    /** 动作扩展负载(JSON字符串) */
    private String payload;

    /** 动作原因 */
    private String reason;

    /** 修订要求 */
    private String requiredFix;

    /**
     * ISO-8601, for example 2026-04-22T12:00:00
     */
    private String deadline;

    /** 请求幂等键，同 meetingId 下唯一 */
    private String clientRequestId;

    /** 端到端链路追踪ID */
    private String traceId;

    /** 期望版本号，用于乐观锁并发控制 */
    private Integer expectedVersion;
}
