package com.dasi.infrastructure.persistent.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会议结论摘要持久化对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMeetingSummary {

    /** 自增主键 */
    private Long id;

    /** 会议业务ID */
    private String meetingId;

    /** 阶段结论 */
    private String conclusion;

    /** 行动项JSON数组 */
    private String actionItemsJson;

    /** 下一轮触发条件 */
    private String nextRoundCondition;

    /** 结论角色 */
    private String concludedByRole;

    /** 结论时间 */
    private LocalDateTime concludedAt;

    /** 逻辑删除标记 */
    private Integer isDeleted;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}

