package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebateRoundSummaryVO {

    private Integer roundNumber;

    private Integer turnCount;

    private String lastSpeakerId;

    private String lastSpeakerName;

    private Boolean waitingForWinner;
}
