package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @Author: xerina
 * @Description: 调度决策值对象
 * 职责：描述调度决策的最终结果，包括建议的发言者及其理由。
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DispatchDecisionVO {

    /** 选定的发言者 ID */
    private String speakerId;

    /** 决策来源 (AT_MENTION / DEBATE_ARBITRATOR / PROBABILITY) */
    private String decisionSource;

    /** 决策理由 (仲裁模式下由 LLM 生成) */
    private String reasoning;

    /** 决策对应的辩论会话 */
    private String sessionId;

    /** 决策生成时的轮次 */
    private Integer roundNumber;

    /** 决策生成时的会话版本 */
    private Integer sessionVersion;

}
