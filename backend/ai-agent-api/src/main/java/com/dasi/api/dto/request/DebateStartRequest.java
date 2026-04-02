package com.dasi.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebateStartRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String roomId;

    private String topic;

    private List<String> proClientIds;

    private List<String> conClientIds;

    private Integer turnsPerRound;
}
