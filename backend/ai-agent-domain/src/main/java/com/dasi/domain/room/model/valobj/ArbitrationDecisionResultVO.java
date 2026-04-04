package com.dasi.domain.room.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: xerina
 * @Description: 仲裁者决策结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArbitrationDecisionResultVO {

    /** 被选中的下一位发言者 clientId */
    private String speakerId;

    /** 仲裁说明 */
    private String reasoning;

    /** 决策来源：LLM_ARBITRATOR / ROUND_ROBIN_FALLBACK */
    private String decisionSource;
}
