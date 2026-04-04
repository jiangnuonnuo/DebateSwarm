package com.dasi.domain.room.service.debate.arbitration.strategy;

import com.dasi.domain.room.model.valobj.ArbitrationDecisionResultVO;
import com.dasi.domain.room.model.valobj.ArbitrationPromptContextVO;

/**
 * @Author: xerina
 * @Description: 仲裁决策策略
 */
public interface IArbitrationDecisionStrategy {

    ArbitrationDecisionResultVO decide(ArbitrationPromptContextVO promptContext);

    String strategyType();
}
