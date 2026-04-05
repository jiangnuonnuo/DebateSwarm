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
import java.util.Map;
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
            你现在是聊天室辩论赛的仲裁者，你的职责不是直接发言，而是只输出下一位应该发言的 clientId。
            你必须严格遵守候选集限制，不能返回名字，不能返回候选集之外的 clientId，不能返回仲裁者自己。

            【辩题】
            %s

            【当前进度】
            第 %d 轮，第 %d/%d 次发言

            【仲裁者】
            %s

            【上一位发言者】
            clientId=%s, side=%s

            【当前槽位要求】
            requiredSide=%s, slotRetryCount=%d

            【正方成员】
            %s

            【反方成员】
            %s

            【本次允许选择的候选 clientId】
            %s

            【当前槽位明确排除】
            %s

            【优先推荐顺序】
            %s

            【候选人入房顺序】
            %s

            【当前轮发言统计】
            %s

            【当前轮历史】
            %s

            【最近房间消息】
            %s

            决策硬约束：
            1. 只能从“本次允许选择的候选 clientId”里返回 1 个 speakerId。
            2. speakerId 必须是 clientId，不是 clientName。
            3. 不能返回仲裁者自己，不能返回上一位刚发言的 clientId。
            4. 若 requiredSide 非空，你只能在该阵营里补位。
            5. 当前槽位明确排除中的 clientId 已经失败过，不能再次选择。
            4. 只能输出 JSON，不要输出 markdown，不要输出代码块，不要补充解释文字。

            决策偏好：
            1. 优先让上一位发言者的对侧阵营回应上一条核心论点。
            2. 在当前候选集中优先选择“优先推荐顺序”更靠前的人。
            3. 如果你没有选择优先推荐顺序中的第一位，需要在 reasoning 中说明原因。
            4. 如果当前是失败补位场景，优先为同一方补位。
            5. reasoning 保持一句话，简短清晰。

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
                safe(promptContext.getLastSpeakerClientId()),
                safe(promptContext.getLastSpeakerSide()),
                safe(promptContext.getRequiredSide()),
                defaultNumber(promptContext.getSlotRetryCount()),
                formatMembers(promptContext.getProMembers()),
                formatMembers(promptContext.getConMembers()),
                formatCandidateIds(promptContext.getCandidateSpeakerIds()),
                formatCandidateIds(promptContext.getExcludedSpeakerIds()),
                formatCandidateIds(promptContext.getPreferredSpeakerIds()),
                formatJoinOrder(promptContext.getCandidateJoinOrder()),
                formatHistoryStats(promptContext.getSpeakerHistoryStats()),
                formatRoundHistory(promptContext.getRoundHistory()),
                formatRecentConversation(promptContext.getRecentConversation())
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

    private String formatCandidateIds(List<String> candidateIds) {
        return candidateIds == null || candidateIds.isEmpty() ? "[]" : JSON.toJSONString(candidateIds);
    }

    private String formatJoinOrder(Map<String, Integer> joinOrderMap) {
        return joinOrderMap == null || joinOrderMap.isEmpty() ? "{}" : JSON.toJSONString(joinOrderMap);
    }

    private String formatHistoryStats(Map<String, Integer> historyStats) {
        return historyStats == null || historyStats.isEmpty() ? "{}" : JSON.toJSONString(historyStats);
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
