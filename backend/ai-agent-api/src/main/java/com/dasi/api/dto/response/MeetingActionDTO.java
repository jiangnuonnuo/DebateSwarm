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
public class MeetingActionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 动作ID */
    private String actionId;

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

    /** 扩展负载(JSON字符串) */
    private String payload;

    /** 动作原因 */
    private String reason;

    /** 修订要求 */
    private String requiredFix;

    /** 截止时间 */
    private String deadline;

    /** 处理结果 */
    private String result;

    /** 动作落库时对应的会议版本 */
    private Integer meetingVersion;

    /** 幂等请求键 */
    private String clientRequestId;

    /** 链路追踪ID */
    private String traceId;

    /** 创建时间 */
    private String createdAt;
}
