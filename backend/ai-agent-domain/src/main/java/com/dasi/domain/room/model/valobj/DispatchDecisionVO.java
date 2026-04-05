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

    /** 本次执行链路的唯一追踪ID，用于过滤幽灵回调 */
    private String dispatchTraceId;

    /** 当前槽位强制要求的阵营；为空表示首发或全体候选 */
    private String requiredSide;

    /** Multi-At 批次追踪ID，仅自由聊天批量 @ 使用 */
    private String batchTraceId;

    /** Multi-At 原始输入顺序 */
    private Integer orderIndex;

    /** 是否属于自由聊天 Multi-At 批次 */
    private Boolean multiAt;

}
