package com.dasi.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebateStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;

    private String topic;

    private String status;

    private Integer currentRound;

    private Integer currentTurn;

    private Integer turnsPerRound;

    private Map<String, String> roundWinners;

    private String arbitratorClientId;

    private String arbitratorName;

    private Boolean waitingForWinner;

    private Integer pendingRoundNumber;

    private DebateRoundSummaryDTO lastRoundSummary;

    private List<DebateMemberDTO> proMembers;

    private List<DebateMemberDTO> conMembers;

    /** 当前是否允许宣布胜方 */
    private Boolean canDeclareWinner;

    /** 当前是否允许开始下一轮 */
    private Boolean canStartNextRound;

    /** 当前是否允许结束辩论 */
    private Boolean canStopDebate;
}
