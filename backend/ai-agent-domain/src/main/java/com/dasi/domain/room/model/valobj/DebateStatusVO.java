package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebateStatusVO {

    private String sessionId;

    private String topic;

    private DebateStatus status;

    private Integer currentRound;

    private Integer currentTurn;

    private Integer turnsPerRound;

    private Map<String, String> roundWinners;

    private String arbitratorClientId;

    private String arbitratorName;

    private Boolean waitingForWinner;

    private Integer pendingRoundNumber;

    private DebateRoundSummaryVO lastRoundSummary;

    private List<DebateMemberStatusVO> proMembers;

    private List<DebateMemberStatusVO> conMembers;
}
