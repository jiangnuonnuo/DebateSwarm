package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private Long lastRoundEndedAt;

    private DebateRoundSummaryVO lastRoundSummary;

    private Integer version;

    public boolean waitingForWinner() {
        return pendingRoundNumber != null && pendingRoundNumber > 0;
    }
}
