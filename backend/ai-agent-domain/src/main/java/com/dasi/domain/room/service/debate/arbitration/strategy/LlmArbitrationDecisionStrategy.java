package com.dasi.domain.room.service.debate.arbitration.strategy;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.ai.service.dispatch.IDispatchService;
import com.dasi.domain.room.model.valobj.ArbitrationDecisionResultVO;
import com.dasi.domain.room.model.valobj.ArbitrationPromptContextVO;
import com.dasi.domain.room.model.valobj.ArbitratorPromptEnvelopeVO;
import com.dasi.domain.room.service.IContextAssemblerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.dasi.domain.ai.model.enumeration.AiArmoryType.ARMORY_CHAT;
import static com.dasi.domain.ai.model.enumeration.AiType.CLIENT;

/**
 * @Author: xerina
 * @Description: LLM 仲裁决策策略（仅负责调用与解析，不负责拼装提示词文本）
 */
@Slf4j
@Service
public class LlmArbitrationDecisionStrategy implements IArbitrationDecisionStrategy {

    private static final String STRATEGY_TYPE = "LLM_ARBITRATOR";

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private IDispatchService aiDispatchService;

    @Resource
    private IContextAssemblerService contextAssemblerService;

    @Override
    public ArbitrationDecisionResultVO decide(ArbitrationPromptContextVO promptContext) {
        String arbitratorClientId = promptContext.getArbitratorClientId();
        String beanName = CLIENT.getBeanName(arbitratorClientId);
        if (!applicationContext.containsBean(beanName)) {
            aiDispatchService.dispatchArmoryStrategy(ARMORY_CHAT.getType(), Collections.singleton(arbitratorClientId));
        }
        ChatClient arbitratorClient = applicationContext.getBean(beanName, ChatClient.class);
        ArbitratorPromptEnvelopeVO promptEnvelope = contextAssemblerService.assembleArbitratorPromptEnvelope(promptContext, arbitratorClientId);

        int candidateCount = promptContext.getCandidateSpeakerIds() == null ? 0 : promptContext.getCandidateSpeakerIds().size();
        log.info("【ARBITRATOR_LLM_CALL】sessionId={}, roomId={}, arbitratorClientId={}, round={}, turn={}, requiredSide={}, retryCount={}, candidateCount={}, mode={}, digest={}",
                promptContext.getSessionId(),
                promptContext.getRoomId(),
                arbitratorClientId,
                defaultNumber(promptContext.getCurrentRound()),
                defaultNumber(promptContext.getCurrentTurn()),
                safe(promptContext.getRequiredSide()),
                defaultNumber(promptContext.getSlotRetryCount()),
                candidateCount,
                safe(promptEnvelope.getMode()),
                safe(promptEnvelope.getInstructionDigest()));

        List<Message> messages = new ArrayList<>();
        if (promptEnvelope.getSystemPrompt() != null && !promptEnvelope.getSystemPrompt().isBlank()) {
            messages.add(new SystemMessage(promptEnvelope.getSystemPrompt()));
        }
        messages.add(new UserMessage(promptEnvelope.getUserPrompt()));
        String rawResponse = arbitratorClient.prompt(new Prompt(messages)).call().content();
        log.debug("【ARBITRATOR_LLM_RAW】sessionId={}, roomId={}, arbitratorClientId={}, raw={}",
                promptContext.getSessionId(),
                promptContext.getRoomId(),
                arbitratorClientId,
                shorten(rawResponse, 500));

        String normalized = normalizeJson(rawResponse);
        JSONObject jsonObject = JSON.parseObject(normalized);
        String speakerId = jsonObject.getString("speakerId");
        String reasoning = jsonObject.getString("reasoning");
        log.info("【ARBITRATOR_LLM_PARSED】sessionId={}, roomId={}, arbitratorClientId={}, speakerId={}, reasoning={}",
                promptContext.getSessionId(),
                promptContext.getRoomId(),
                arbitratorClientId,
                speakerId,
                reasoning);
        return ArbitrationDecisionResultVO.builder()
                .speakerId(speakerId)
                .reasoning(reasoning)
                .decisionSource(strategyType())
                .build();
    }

    @Override
    public String strategyType() {
        return STRATEGY_TYPE;
    }

    private String normalizeJson(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalStateException("仲裁者返回为空");
        }
        String normalized = rawResponse.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```json", "")
                    .replaceFirst("^```", "")
                    .replaceFirst("```$", "")
                    .trim();
        }
        return normalized;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int defaultNumber(Integer value) {
        return value == null ? 0 : value;
    }

    private String shorten(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "...";
    }
}
