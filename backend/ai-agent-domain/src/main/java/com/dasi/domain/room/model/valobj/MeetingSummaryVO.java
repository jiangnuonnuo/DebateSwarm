package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetingSummaryVO {

    private String conclusion;

    private List<String> actionItems;

    private String nextRoundCondition;

    private String concludedByRole;

    private LocalDateTime concludedAt;
}

