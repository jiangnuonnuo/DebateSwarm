package com.dasi.domain.room.service.context;

import com.dasi.domain.ai.model.vo.AiPromptVO;
import com.dasi.domain.ai.repository.IAiRepository;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.model.valobj.ArbitrationPromptContextVO;
import com.dasi.domain.room.model.valobj.ArbitratorPromptEnvelopeVO;
import com.dasi.domain.room.model.valobj.DebateMemberStatusVO;
import com.dasi.domain.room.model.valobj.DebateTurnRecordVO;
import com.dasi.domain.room.model.valobj.RoomRuntimePromptContextVO;
import com.dasi.domain.room.service.IContextAssemblerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.context
 * @Author: xerina
 * @CreateTime: 2026-03-23  11:03
 * @Description: 上下文信息服务实现类 (专业场景化装配版)
 */
@Slf4j
@Service
public class ContextAssemblerService implements IContextAssemblerService {

    @Resource
    private IChatRoomRepository chatRoomRepository;

    @Resource
    private IAiRepository aiRepository;

    @Resource
    private RoomRuntimeHeaderAssembler roomRuntimeHeaderAssembler;

    @Resource
    private DebateInstructionAssembler debateInstructionAssembler;

    /**
     * 上下文消息限制条数
     */
    private static final Integer CONTEXT_LIMIT = 20;

    private static final String ARBITRATOR_USER_PROMPT_TEMPLATE =
            """
            你现在是聊天室辩论赛的仲裁者，你的职责不是直接发言，而是只输出下一位应该发言的 clientId。
            你必须严格遵守候选集限制，不能返回名字，不能返回候选集之外的 clientId，不能返回仲裁者自己。

            【辩题】
            %s

            【当前进度】
            第 %d 轮，第 %d/%d 次发言

            【运行时动态仲裁指令】
            %s

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

    @Override
    public List<Message> assemble(String roomId, String agentId, String roomName, String currentMemberName) {
        return assemble(roomId, agentId, roomName, currentMemberName, null);
    }

    @Override
    public List<Message> assemble(String roomId,
                                  String agentId,
                                  String roomName,
                                  String currentMemberName,
                                  RoomRuntimePromptContextVO runtimePromptContext) {
        // 1. 获取房间详情 (已接入缓存)
        AiChatRoomEntity roomEntity = chatRoomRepository.queryRoomById(roomId);
        String roomDesc = (roomEntity != null && roomEntity.getRoomDesc() != null) ? roomEntity.getRoomDesc() : "暂无背景描述";

        // 2. 从仓储层拉取最近的消息流 (DESC 排序)
        List<AiChatRoomMessageEntity> messages = chatRoomRepository.queryContextMessages(roomId, CONTEXT_LIMIT);
        if (messages == null) {
            messages = new ArrayList<>();
        }

        // 3. 倒序处理，确保上下文是按时间正序排列 (从旧到新)
        Collections.reverse(messages);

        List<Message> messageList = new ArrayList<>();

        // 4. 构建运行时头消息 (UserMessage 注入，单次请求生效)
        String runtimeHeader = roomRuntimeHeaderAssembler.build(roomId, roomName, roomDesc, agentId, currentMemberName, runtimePromptContext);
        messageList.add(new UserMessage(runtimeHeader));

        // 5. 遍历组装结构化的对话历史 (统一为 UserMessage，采用 [名称(ID)] 格式)
        for (AiChatRoomMessageEntity msg : messages) {
            String formattedContent;
            String senderName = msg.getSenderName();
            String senderId = msg.getSenderId();

            if (senderId.equals(agentId)) {
                // 标记“自我”发言
                formattedContent = String.format("[%s(%s) (你)]: %s", senderName, senderId, msg.getContent());
            } else {
                // 标记“他人”发言
                formattedContent = String.format("[%s(%s)]: %s", senderName, senderId, msg.getContent());
            }
            messageList.add(new UserMessage(formattedContent));
        }

        log.debug("【PROMPT_RUNTIME_ASSEMBLE】roomId={}, clientId={}, stage={}, sessionId={}, traceId={}, totalMessages={}",
                roomId,
                agentId,
                runtimePromptContext == null ? "FREE_CHAT" : safe(runtimePromptContext.getStage()),
                runtimePromptContext == null ? "" : safe(runtimePromptContext.getSessionId()),
                runtimePromptContext == null ? "" : safe(runtimePromptContext.getTraceId()),
                messageList.size());
        return messageList;
    }

    @Override
    public void recordMessage(AiChatRoomMessageEntity messageEntity) {
        log.info("【消息持久化】房间={}，发送者={}，内容长度={}",
                messageEntity.getRoomId(), messageEntity.getSenderName(),
                messageEntity.getContent() != null ? messageEntity.getContent().length() : 0);
        chatRoomRepository.saveMessage(messageEntity);
    }

    @Override
    public String queryRoomExtConfig(String roomId) {
        return chatRoomRepository.queryExtConfigByRoomId(roomId);
    }

    @Override
    public ArbitratorPromptEnvelopeVO assembleArbitratorPromptEnvelope(ArbitrationPromptContextVO promptContext,
                                                                       String arbitratorClientId) {
        String debateInstruction = debateInstructionAssembler.buildInstruction(promptContext);
        String instructionDigest = debateInstructionAssembler.buildDigest(debateInstruction);
        String staticSystemPrompt = sanitizeArbitratorStaticPrompt(queryStaticSystemPrompt(arbitratorClientId), arbitratorClientId);
        String mode;
        String systemPrompt;
        if (staticSystemPrompt.isBlank()) {
            mode = "FALLBACK_ONLY";
            systemPrompt = """
                    你是聊天室辩论赛的仲裁者，只负责返回下一位 speakerId。
                    %s
                    """.formatted(debateInstruction);
        } else if (staticSystemPrompt.contains("{debateInstruction}")) {
            mode = "PLACEHOLDER_REPLACED";
            systemPrompt = staticSystemPrompt.replace("{debateInstruction}", debateInstruction);
        } else {
            mode = "APPENDED";
            systemPrompt = staticSystemPrompt + "\n\n# 运行时仲裁动态指令\n" + debateInstruction;
        }

        int candidateCount = promptContext.getCandidateSpeakerIds() == null ? 0 : promptContext.getCandidateSpeakerIds().size();
        log.info("【ARBITRATOR_PROMPT_READY】roomId={}, sessionId={}, round={}, turn={}, candidateCount={}, requiredSide={}, retryCount={}, mode={}, digest={}",
                promptContext.getRoomId(),
                promptContext.getSessionId(),
                defaultNumber(promptContext.getCurrentRound()),
                defaultNumber(promptContext.getCurrentTurn()),
                candidateCount,
                safe(promptContext.getRequiredSide()),
                defaultNumber(promptContext.getSlotRetryCount()),
                mode,
                instructionDigest);
        log.debug("【ARBITRATOR_PROMPT_CONTEXT】roomId={}, sessionId={}, candidates={}, preferred={}, excluded={}, instruction={}",
                promptContext.getRoomId(),
                promptContext.getSessionId(),
                promptContext.getCandidateSpeakerIds(),
                promptContext.getPreferredSpeakerIds(),
                promptContext.getExcludedSpeakerIds(),
                debateInstruction);

        String userPrompt = ARBITRATOR_USER_PROMPT_TEMPLATE.formatted(
                safe(promptContext.getTopic()),
                defaultNumber(promptContext.getCurrentRound()),
                defaultNumber(promptContext.getCurrentTurn()),
                defaultNumber(promptContext.getTurnsPerRound()),
                debateInstruction,
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

        return ArbitratorPromptEnvelopeVO.builder()
                .systemPrompt(systemPrompt)
                .userPrompt(userPrompt)
                .mode(mode)
                .instructionDigest(instructionDigest)
                .build();
    }

    private String sanitizeArbitratorStaticPrompt(String staticPrompt, String clientId) {
        if (staticPrompt == null) {
            return "";
        }
        String normalized = staticPrompt.trim();
        if (normalized.isBlank()) {
            return "";
        }
        int placeholderCount = countOccurrences(normalized, "{debateInstruction}");
        int personaCount = countOccurrences(normalized, "# 1. 核心人设");
        if (normalized.length() > 5000 || placeholderCount > 1 || personaCount > 1) {
            log.warn("【ARBITRATOR_STATIC_PROMPT_SANITIZED】clientId={}, length={}, placeholderCount={}, personaCount={}, action=FALLBACK_ONLY",
                    clientId, normalized.length(), placeholderCount, personaCount);
            return "";
        }
        return normalized;
    }

    private int countOccurrences(String text, String token) {
        if (text == null || token == null || token.isEmpty()) {
            return 0;
        }
        int count = 0;
        int idx = 0;
        while (idx >= 0) {
            idx = text.indexOf(token, idx);
            if (idx >= 0) {
                count++;
                idx += token.length();
            }
        }
        return count;
    }

    private String queryStaticSystemPrompt(String clientId) {
        AiPromptVO promptVO = aiRepository.queryPromptByClientId(clientId);
        if (promptVO == null || promptVO.getSystemPrompt() == null) {
            return "";
        }
        return promptVO.getSystemPrompt();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int defaultNumber(Integer value) {
        return value == null ? 0 : value;
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
        if (candidateIds == null || candidateIds.isEmpty()) {
            return "[]";
        }
        return candidateIds.toString();
    }

    private String formatJoinOrder(Map<String, Integer> joinOrderMap) {
        if (joinOrderMap == null || joinOrderMap.isEmpty()) {
            return "{}";
        }
        return joinOrderMap.toString();
    }

    private String formatHistoryStats(Map<String, Integer> historyStats) {
        if (historyStats == null || historyStats.isEmpty()) {
            return "{}";
        }
        return historyStats.toString();
    }
}
