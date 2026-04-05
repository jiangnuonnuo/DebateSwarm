package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomDebateStateVO {

    private String arbitratorClientId;

    private String activeDebateSessionId;

    private Integer pendingRoundNumber;

    private Integer pendingRoundTurnCount;

    private String pendingSpeakerId;

    private Integer pendingTurnNumber;

    private String pendingDecisionSource;

    private String pendingDecisionReasoning;

    private String dispatchSessionId;

    private Integer dispatchRound;

    private Integer dispatchVersion;

    private String dispatchTraceId;

    /**
     * 当前逻辑槽位要求的阵营。
     * 为空表示新一槽由上一位成功发言者决定对侧，或首发全体可选。
     */
    private String slotRequiredSide;

    /**
     * 当前槽位已经尝试过的 speakerId。
     * 用于失败后同槽重选，避免再次选中相同辩手。
     */
    private List<String> slotAttemptedSpeakerIds;

    /**
     * 当前槽位已尝试次数。
     * 仅用于日志、提示词和排查，不影响轮次主键。
     */
    private Integer slotRetryCount;

    private Long lastRoundEndedAt;

    private DebateRoundSummaryVO lastRoundSummary;

    private Integer version;

    public boolean waitingForWinner() {
        return pendingRoundNumber != null && pendingRoundNumber > 0;
    }
}
