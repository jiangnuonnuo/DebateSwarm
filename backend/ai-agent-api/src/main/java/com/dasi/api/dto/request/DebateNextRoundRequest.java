package com.dasi.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 下一轮辩论启动请求。
 * 仅 roomId 必填；正反方与发言次数可选，用于轮间重配。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebateNextRoundRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String roomId;

    private List<String> proClientIds;

    private List<String> conClientIds;

    private Integer turnsPerRound;
}
