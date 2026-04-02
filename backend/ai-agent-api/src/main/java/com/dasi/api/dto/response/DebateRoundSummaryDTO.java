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
public class DebateRoundSummaryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer roundNumber;

    private Integer turnCount;

    private String lastSpeakerId;

    private String lastSpeakerName;

    private Boolean waitingForWinner;
}
