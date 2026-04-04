package com.dasi.domain.room.service.debate.arbitration.strategy;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.ai.service.dispatch.IDispatchService;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.model.valobj.ArbitrationDecisionResultVO;
import com.dasi.domain.room.model.valobj.ArbitrationPromptContextVO;
import com.dasi.domain.room.model.valobj.DebateMemberStatusVO;
import com.dasi.domain.room.model.valobj.DebateTurnRecordVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.dasi.domain.ai.model.enumeration.AiArmoryType.ARMORY_CHAT;
import static com.dasi.domain.ai.model.enumeration.AiType.CLIENT;

/**
 * @Author: xerina
 * @Description: LLM 仲裁决策策略
 */
@Slf4j
@Service
public class LlmArbitrationDecisionStrategy implements IArbitrationDecisionStrategy {

    private static final String STRATEGY_TYPE = "LLM_ARBITRATOR";

    private static final String PROMPT_TEMPLATE =
            """
            你现在是聊天室辩论赛的仲裁者，你的职责不是直接发言，而是决定下一位应该发言的 clientId。
            你必须严格基于给定候选集返回 JSON，不能返回候选集之外的 clientId。

            【辩题】
            %s

            【当前进度】
            第 %d 轮，第 %d/%d 次发言

            【仲裁者】
            %s

            【正方成员】
            %s

            【反方成员】
            %s

            【当前轮历史】
            %s

            【最近房间消息】
            %s

            【本次允许选择的候选 clientId】
            %s

            决策要求：
            1. 只能从给定候选 clientId 中选择下一位发言者。
            2. 优先保持辩论平衡和话题延续性。
            3. reasoning 要简短清晰，直接说明为什么让该 clientId 发言。
            4. 不要输出 markdown，不要解释，不要代码块。

            只返回一个合法 JSON：
            {"speakerId":"client_xxx","reasoning":"..."}
            """;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private IDispatchService aiDispatchService;

    @Override
    public ArbitrationDecisionResultVO decide(ArbitrationPromptContextVO promptContext) {
        String arbitratorClientId = promptContext.getArbitratorClientId();
        String beanName = CLIENT.getBeanName(arbitratorClientId);
        if (!applicationContext.containsBean(beanName)) {
            aiDispatchService.dispatchArmoryStrategy(ARMORY_CHAT.getType(), Collections.singleton(arbitratorClientId));
        }
        ChatClient arbitratorClient = applicationContext.getBean(beanName, ChatClient.class);
        String rawResponse = arbitratorClient.prompt(buildPrompt(promptContext)).call().content();
        log.info("仲裁者 client{}，房间 = {} 仲裁结果为={}", arbitratorClientId,promptContext.getRoomId() , rawResponse);
        String normalized = normalizeJson(rawResponse);
        JSONObject jsonObject = JSON.parseObject(normalized);
        return ArbitrationDecisionResultVO.builder()
                .speakerId(jsonObject.getString("speakerId"))
                .reasoning(jsonObject.getString("reasoning"))
                .decisionSource(strategyType())
                .build();
    }

    @Override
    public String strategyType() {
        return STRATEGY_TYPE;
    }

    private String buildPrompt(ArbitrationPromptContextVO promptContext) {
        return PROMPT_TEMPLATE.formatted(
                safe(promptContext.getTopic()),
                defaultNumber(promptContext.getCurrentRound()),
                defaultNumber(promptContext.getCurrentTurn()),
                defaultNumber(promptContext.getTurnsPerRound()),
                safe(promptContext.getArbitratorClientId()),
                formatMembers(promptContext.getProMembers()),
                formatMembers(promptContext.getConMembers()),
                formatRoundHistory(promptContext.getRoundHistory()),
                formatRecentConversation(promptContext.getRecentConversation()),
                promptContext.getCandidateSpeakerIds() == null ? "[]" : JSON.toJSONString(promptContext.getCandidateSpeakerIds())
        );
    }

    private String formatMembers(List<DebateMemberStatusVO> members) {
        if (members == null || members.isEmpty()) {
            return "[]";
        }
        return members.stream()
                .map(member -> "%s(%s)".formatted(safe(member.getClientName()), safe(member.getClientId())))
                .collect(Collectors.joining(", "));
    }

    private String formatRoundHistory(List<DebateTurnRecordVO> roundHistory) {
        if (roundHistory == null || roundHistory.isEmpty()) {
            return "本轮尚无发言记录";
        }
        return roundHistory.stream()
                .map(record -> "[%s][%s(%s)]: %s".formatted(
                        safe(record.getSide()),
                        safe(record.getSpeakerName()),
                        safe(record.getSpeakerId()),
                        safe(record.getContent())))
                .collect(Collectors.joining("\n"));
    }

    private String formatRecentConversation(List<AiChatRoomMessageEntity> recentConversation) {
        if (recentConversation == null || recentConversation.isEmpty()) {
            return "暂无最近房间消息";
        }
        return recentConversation.stream()
                .map(message -> "[%s][%s(%s)]: %s".formatted(
                        safe(message.getSenderType()),
                        safe(message.getSenderName()),
                        safe(message.getSenderId()),
                        safe(message.getContent())))
                .collect(Collectors.joining("\n"));
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
}
