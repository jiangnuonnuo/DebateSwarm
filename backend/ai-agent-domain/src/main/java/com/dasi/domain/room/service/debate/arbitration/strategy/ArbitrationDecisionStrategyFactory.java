package com.dasi.domain.room.service.debate.arbitration.strategy;

import com.dasi.domain.room.model.valobj.ArbitrationDecisionResultVO;
import com.dasi.domain.room.model.valobj.ArbitrationPromptContextVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author: xerina
 * @Description: 仲裁决策策略工厂
 */
@Slf4j
@Service
public class ArbitrationDecisionStrategyFactory {

    @Resource
    private LlmArbitrationDecisionStrategy llmArbitrationDecisionStrategy;

    @Resource
    private RoundRobinFallbackDecisionStrategy roundRobinFallbackDecisionStrategy;

    public ArbitrationDecisionResultVO decide(ArbitrationPromptContextVO promptContext) {
        try {
            ArbitrationDecisionResultVO result = llmArbitrationDecisionStrategy.decide(promptContext);
            validate(promptContext, result);
            return result;
        } catch (Exception e) {
            log.warn("【辩论仲裁】LLM 决策失败，降级轮转策略：sessionId={}, reason={}",
                    promptContext.getSessionId(), e.getMessage());
            return roundRobinFallbackDecisionStrategy.decide(promptContext);
        }
    }

    private void validate(ArbitrationPromptContextVO promptContext, ArbitrationDecisionResultVO result) {
        if (result == null) {
            throw new IllegalStateException("仲裁结果为空");
        }
        if (result.getSpeakerId() == null || result.getSpeakerId().isBlank()) {
            throw new IllegalStateException("仲裁结果缺少 speakerId");
        }
        if (promptContext.getCandidateSpeakerIds() == null || !promptContext.getCandidateSpeakerIds().contains(result.getSpeakerId())) {
            throw new IllegalStateException("仲裁结果不在候选集内");
        }
        if (result.getReasoning() == null || result.getReasoning().isBlank()) {
            result.setReasoning("请下一位候选辩手继续围绕当前观点展开回应。");
        }
    }
}
