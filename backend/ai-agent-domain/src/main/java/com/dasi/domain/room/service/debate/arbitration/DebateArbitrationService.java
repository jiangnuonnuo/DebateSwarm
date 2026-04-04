package com.dasi.domain.room.service.debate.arbitration;

import com.dasi.domain.room.model.valobj.ArbitrationDecisionResultVO;
import com.dasi.domain.room.model.valobj.ArbitrationPromptContextVO;
import com.dasi.domain.room.service.debate.arbitration.strategy.ArbitrationDecisionStrategyFactory;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * @Author: xerina
 * @Description: 辩论仲裁服务实现
 */
@Service
public class DebateArbitrationService implements IDebateArbitrationService {

    @Resource
    private ArbitrationDecisionStrategyFactory arbitrationDecisionStrategyFactory;

    @Override
    public ArbitrationDecisionResultVO arbitrate(ArbitrationPromptContextVO promptContext) {
        return arbitrationDecisionStrategyFactory.decide(promptContext);
    }
}
