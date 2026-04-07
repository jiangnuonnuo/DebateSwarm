package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 轮间重配配置：
 * - 正反方可选重配（需同时提供）
 * - 每轮发言次数可选重配
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DebateRoundConfigVO {

    private List<String> proClientIds;

    private List<String> conClientIds;

    private Integer turnsPerRound;
}
