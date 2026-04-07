package com.dasi.domain.room.service.debate.arbitration.strategy;

import com.dasi.domain.room.model.valobj.ArbitrationDecisionResultVO;
import com.dasi.domain.room.model.valobj.ArbitrationPromptContextVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

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

    @Value("${room.debate.arbitrator-timeout-seconds:45}")
    private long arbitratorTimeoutSeconds;

    public ArbitrationDecisionResultVO decide(ArbitrationPromptContextVO promptContext) {
        try {
            ArbitrationDecisionResultVO result = CompletableFuture
                    .supplyAsync(() -> llmArbitrationDecisionStrategy.decide(promptContext))
                    .orTimeout(arbitratorTimeoutSeconds, TimeUnit.SECONDS)
                    .join();
            validate(promptContext, result);
            log.info("【ARBITRATOR_DECISION_ACCEPTED】sessionId={}, roomId={}, source={}, speakerId={}, reasoning={}",
                    promptContext.getSessionId(),
                    promptContext.getRoomId(),
                    result.getDecisionSource(),
                    result.getSpeakerId(),
                    result.getReasoning());
            return result;
        } catch (Exception e) {
            log.warn("【ARBITRATOR_DECISION_FALLBACK】sessionId={}, roomId={}, reason={}",
                    promptContext.getSessionId(), promptContext.getRoomId(), unwrapMessage(e));
            ArbitrationDecisionResultVO fallback = roundRobinFallbackDecisionStrategy.decide(promptContext);
            log.info("【ARBITRATOR_DECISION_FALLBACK_RESULT】sessionId={}, roomId={}, source={}, speakerId={}, reasoning={}",
                    promptContext.getSessionId(),
                    promptContext.getRoomId(),
                    fallback.getDecisionSource(),
                    fallback.getSpeakerId(),
                    fallback.getReasoning());
            return fallback;
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

    private String unwrapMessage(Exception e) {
        Throwable cause = e;
        if (e instanceof CompletionException completionException && completionException.getCause() != null) {
            cause = completionException.getCause();
        }
        return cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : cause.getMessage();
    }
}
