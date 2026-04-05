package com.dasi.domain.room.service.debate;

import com.alibaba.fastjson2.JSON;
import com.dasi.domain.room.apapter.port.IRoomEventPublisher;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.model.entity.DebateRecordEntity;
import com.dasi.domain.room.model.entity.DebateSessionEntity;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.model.valobj.ArbitrationDecisionResultVO;
import com.dasi.domain.room.model.valobj.ArbitrationPromptContextVO;
import com.dasi.domain.room.model.valobj.DebateContextVO;
import com.dasi.domain.room.model.valobj.DebateMemberStatusVO;
import com.dasi.domain.room.model.valobj.DebateStatus;
import com.dasi.domain.room.model.valobj.DebateStatusVO;
import com.dasi.domain.room.model.valobj.DebateTurnRecordVO;
import com.dasi.domain.room.model.valobj.DispatchDecisionVO;
import com.dasi.domain.room.model.valobj.RoomDebateStateVO;
import com.dasi.domain.room.model.valobj.WebSocketEvent;
import com.dasi.domain.room.service.IRoomChatService;
import com.dasi.domain.room.service.debate.arbitration.IDebateArbitrationService;
import com.dasi.domain.room.service.dispatch.DispatchContext;
import com.dasi.types.exception.DependencyConflictException;
import com.dasi.types.exception.MissingException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DebateService implements IDebateService {

    private static final int DEFAULT_TURNS_PER_ROUND = 6;
    private static final int RECORD_REASONING_LIMIT = 96;

    @Resource
    private IChatRoomRepository chatRoomRepository;

    @Resource
    private IRoomChatService roomChatService;

    @Resource
    private IDebateArbitrationService debateArbitrationService;

    @Resource
    private IRoomEventPublisher roomEventPublisher;

    @Override
    @Transactional
    public void setArbitrator(String roomId, String clientId) {
        ensureRoomExists(roomId);
        validateClientMember(roomId, clientId);

        DebateSessionEntity activeSession = chatRoomRepository.queryActiveDebateSession(roomId);
        if (activeSession != null) {
            throw new DependencyConflictException("当前房间已有进行中的辩论，无法切换仲裁者");
        }

        RoomDebateStateVO state = loadRoomDebateState(roomId);
        state.setArbitratorClientId(clientId);
        clearRoundState(state);
        clearPendingDispatch(state);
        persistRoomDebateState(roomId, state);
    }

    @Override
    @Transactional
    public void removeArbitrator(String roomId) {
        ensureRoomExists(roomId);
        DebateSessionEntity activeSession = chatRoomRepository.queryActiveDebateSession(roomId);
        if (activeSession != null) {
            throw new DependencyConflictException("当前房间辩论进行中，无法移除仲裁者");
        }

        RoomDebateStateVO state = loadRoomDebateState(roomId);
        state.setArbitratorClientId(null);
        state.setActiveDebateSessionId(null);
        clearRoundState(state);
        clearPendingDispatch(state);
        persistRoomDebateState(roomId, state);
    }

    @Override
    @Transactional
    public String startDebate(String roomId, String topic, List<String> proClientIds, List<String> conClientIds, Integer turnsPerRound) {
        ensureRoomExists(roomId);
        RoomDebateStateVO state = loadRoomDebateState(roomId);
        if (state.getArbitratorClientId() == null || state.getArbitratorClientId().isBlank()) {
            throw new MissingException("请先设置仲裁者");
        }
        validateClientMember(roomId, state.getArbitratorClientId());

        DebateSessionEntity activeSession = chatRoomRepository.queryActiveDebateSession(roomId);
        if (activeSession != null) {
            throw new DependencyConflictException("当前房间已有进行中的辩论");
        }

        List<String> normalizedPro = normalizeDebaters(proClientIds);
        List<String> normalizedCon = normalizeDebaters(conClientIds);
        validateDebateSides(roomId, state.getArbitratorClientId(), normalizedPro, normalizedCon);

        DebateSessionEntity session = DebateSessionEntity.builder()
                .sessionId(buildSessionId())
                .roomId(roomId)
                .topic(normalizeTopic(topic))
                .arbitratorClientId(state.getArbitratorClientId())
                .proClientIds(normalizedPro)
                .conClientIds(normalizedCon)
                .turnsPerRound(normalizeTurnsPerRound(turnsPerRound))
                .currentRound(0)
                .currentTurn(0)
                .roundWinners(new ArrayList<>())
                .status(DebateStatus.PENDING)
                .version(0)
                .build();
        session.startFirstRound();
        chatRoomRepository.saveDebateSession(session);

        state.setActiveDebateSessionId(session.getSessionId());
        clearRoundState(state);
        clearPendingDispatch(state);
        persistRoomDebateState(roomId, state);

        runAfterCommit(() -> {
            roomChatService.publishSystemNotice(
                    roomId,
                    "DEBATE_START",
                    String.format("辩论开始：%s。第 %d 轮开始，仲裁者将决定首位发言者。", session.getTopic(), session.getCurrentRound()),
                    buildNoticeExtData("DEBATE_START", session, null, null, false, null)
            );
            publishDispatchTrigger(roomId);
        });

        return session.getSessionId();
    }

    @Override
    @Transactional
    public void declareRoundWinner(String roomId, String winnerSide) {
        DebateSessionEntity session = getRequiredActiveSession(roomId);
        String normalizedWinnerSide = normalizeWinnerSide(winnerSide);
        session.declareRoundWinner(normalizedWinnerSide);
        if (!chatRoomRepository.updateDebateSessionStatus(session)) {
            throw new DependencyConflictException("辩论状态已变更，请刷新后重试");
        }
        advanceSessionVersion(session);

        RoomDebateStateVO state = loadRoomDebateState(roomId);
        clearRoundState(state);
        clearPendingDispatch(state);
        if (state.getLastRoundSummary() != null) {
            state.getLastRoundSummary().setWaitingForWinner(Boolean.FALSE);
        }
        persistRoomDebateState(roomId, state);

        runAfterCommit(() -> roomChatService.publishSystemNotice(
                roomId,
                "ROUND_WINNER",
                String.format("第 %d 轮胜方：%s。", session.getCurrentRound(), "PRO".equals(normalizedWinnerSide) ? "正方" : "反方"),
                buildWinnerExtData(session, normalizedWinnerSide)
        ));
    }

    @Override
    @Transactional
    public void startNextRound(String roomId) {
        DebateSessionEntity session = getRequiredActiveSession(roomId);
        session.startNextRound();
        if (!chatRoomRepository.updateDebateSessionStatus(session)) {
            throw new DependencyConflictException("辩论状态已变更，请刷新后重试");
        }
        advanceSessionVersion(session);

        RoomDebateStateVO state = loadRoomDebateState(roomId);
        clearRoundState(state);
        clearPendingDispatch(state);
        if (state.getLastRoundSummary() != null) {
            state.getLastRoundSummary().setWaitingForWinner(Boolean.FALSE);
        }
        persistRoomDebateState(roomId, state);

        runAfterCommit(() -> {
            roomChatService.publishSystemNotice(
                    roomId,
                    "ROUND_NEXT",
                    String.format("第 %d 轮开始。", session.getCurrentRound()),
                    buildNoticeExtData("ROUND_NEXT", session, null, null, false, null)
            );
            publishDispatchTrigger(roomId);
        });
    }

    @Override
    @Transactional
    public void stopDebate(String roomId) {
        ensureRoomExists(roomId);
        RoomDebateStateVO state = loadRoomDebateState(roomId);

        DebateSessionEntity session = null;
        if (state.getActiveDebateSessionId() != null && !state.getActiveDebateSessionId().isBlank()) {
            session = chatRoomRepository.queryDebateSessionBySessionId(state.getActiveDebateSessionId());
        }
        if (session == null) {
            session = chatRoomRepository.queryActiveDebateSession(roomId);
        }
        if (session != null && !session.isFinished()) {
            session.finishDebate();
            if (!chatRoomRepository.updateDebateSessionStatus(session)) {
                throw new DependencyConflictException("辩论状态已变更，请刷新后重试");
            }
            advanceSessionVersion(session);
        }

        state.setActiveDebateSessionId(null);
        clearRoundState(state);
        clearPendingDispatch(state);
        state.setLastRoundSummary(null);
        persistRoomDebateState(roomId, state);

        runAfterCommit(() -> roomChatService.publishSystemNotice(
                roomId,
                "DEBATE_STOP",
                "辩论已结束，房间恢复自由聊天。",
                buildSimpleExtData("DEBATE_STOP", roomId)
        ));
    }

    @Override
    public DebateStatusVO queryDebateStatus(String roomId) {
        ensureRoomExists(roomId);
        RoomDebateStateVO state = loadRoomDebateState(roomId);

        DebateSessionEntity session = null;
        if (state.getActiveDebateSessionId() != null && !state.getActiveDebateSessionId().isBlank()) {
            session = chatRoomRepository.queryDebateSessionBySessionId(state.getActiveDebateSessionId());
        }
        if (session == null) {
            session = chatRoomRepository.queryActiveDebateSession(roomId);
        }

        if (session == null) {
            Map<String, String> nameMap = buildClientNameMap(roomId, state.getArbitratorClientId() == null
                    ? Collections.emptyList()
                    : List.of(state.getArbitratorClientId()));
            return DebateStatusVO.builder()
                    .arbitratorClientId(state.getArbitratorClientId())
                    .arbitratorName(nameMap.get(state.getArbitratorClientId()))
                    .waitingForWinner(state.waitingForWinner())
                    .pendingRoundNumber(state.getPendingRoundNumber())
                    .lastRoundSummary(state.getLastRoundSummary())
                    .roundWinners(new LinkedHashMap<>())
                    .proMembers(new ArrayList<>())
                    .conMembers(new ArrayList<>())
                    .build();
        }

        List<String> allClientIds = new ArrayList<>();
        allClientIds.add(session.getArbitratorClientId());
        allClientIds.addAll(session.getAllDebaterIds());
        Map<String, String> nameMap = buildClientNameMap(roomId, allClientIds);

        return DebateStatusVO.builder()
                .sessionId(session.getSessionId())
                .topic(session.getTopic())
                .status(session.getStatus())
                .currentRound(session.getCurrentRound())
                .currentTurn(session.getCurrentTurn())
                .turnsPerRound(session.getTurnsPerRound())
                .roundWinners(buildRoundWinnerMap(session.safeRoundWinners()))
                .arbitratorClientId(session.getArbitratorClientId())
                .arbitratorName(nameMap.get(session.getArbitratorClientId()))
                .waitingForWinner(state.waitingForWinner())
                .pendingRoundNumber(state.getPendingRoundNumber())
                .lastRoundSummary(state.getLastRoundSummary())
                .proMembers(toMemberStatusVO(session.getProClientIds(), nameMap))
                .conMembers(toMemberStatusVO(session.getConClientIds(), nameMap))
                .build();
    }

    @Override
    @Transactional
    public DispatchDecisionVO decideNextDispatch(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        if (strategyEntity == null) {
            return null;
        }

        String eventType = strategyEntity.getEventType();
        if (!WebSocketEvent.EventType.CLIENT_MSG_END.equals(eventType)
                && !WebSocketEvent.EventType.CLIENT_MSG_ERROR.equals(eventType)
                && !WebSocketEvent.EventType.DEBATE_DISPATCH_TRIGGER.equals(eventType)) {
            return null;
        }

        String roomId = strategyEntity.getRoomId();
        DebateSessionEntity session = dispatchContext == null ? null : dispatchContext.getDebateSession();
        if (session == null) {
            session = chatRoomRepository.queryActiveDebateSession(roomId);
        }
        if (session == null || !session.isRunning()) {
            log.info("【ARBITRATOR_DECIDE】无活跃辩论可继续 roomId={}, eventType={}", roomId, eventType);
            return null;
        }

        RoomDebateStateVO state = loadRoomDebateState(roomId);
        if (!Objects.equals(state.getActiveDebateSessionId(), session.getSessionId())) {
            log.info("【ARBITRATOR_DECIDE】房间运行态已变更，放弃旧调度 roomId={}, sessionId={}, activeSessionId={}",
                    roomId, session.getSessionId(), state.getActiveDebateSessionId());
            return null;
        }

        if (WebSocketEvent.EventType.DEBATE_DISPATCH_TRIGGER.equals(eventType)) {
            return arbitrateNextSpeaker(session, null);
        }

        AiChatRoomMessageEntity lastMessage = strategyEntity.getCurrentMessage();
        if (lastMessage == null || !session.validateDebater(lastMessage.getSenderId())) {
            log.info("【ARBITRATOR_DECIDE】忽略非辩手或空消息 roomId={}, eventType={}", roomId, eventType);
            return null;
        }
        if (!isExpectedSpeaker(state, lastMessage.getSenderId())) {
            log.info("【ARBITRATOR_DECIDE】忽略过期或越权辩手回流 roomId={}, senderId={}, pendingSpeakerId={}",
                    roomId, lastMessage.getSenderId(), state.getPendingSpeakerId());
            return null;
        }

        if (WebSocketEvent.EventType.CLIENT_MSG_END.equals(eventType)) {
            saveDebateRecord(session, roomId, state, lastMessage);
        } else {
            final DebateSessionEntity currentSession = session;
            final AiChatRoomMessageEntity failedMessage = lastMessage;
            runAfterCommit(() -> roomChatService.publishSystemNotice(
                    roomId,
                    "SPEAKER_ERROR",
                    String.format("辩手 %s 本次发言执行失败，本轮将继续推进下一位发言者。", failedMessage.getSenderName()),
                    buildSpeakerErrorExtData(currentSession, failedMessage)
            ));
        }

        session.recordTurnFinished();
        clearPendingDispatch(state);

        if (session.isRoundComplete()) {
            session.finishCurrentRound();
            if (!chatRoomRepository.updateDebateSessionStatus(session)) {
                throw new DependencyConflictException("辩论状态已变更，请刷新后重试");
            }
            advanceSessionVersion(session);

            state.setActiveDebateSessionId(session.getSessionId());
            state.setPendingRoundNumber(session.getCurrentRound());
            state.setPendingRoundTurnCount(session.getCurrentTurn());
            state.setLastRoundEndedAt(Instant.now().toEpochMilli());
            state.setLastRoundSummary(session.buildRoundSummary(lastMessage.getSenderId(), lastMessage.getSenderName()));
            persistRoomDebateState(roomId, state);

            final DebateSessionEntity currentSession = session;
            final AiChatRoomMessageEntity roundEndMessage = lastMessage;
            runAfterCommit(() -> roomChatService.publishSystemNotice(
                    roomId,
                    "ROUND_END",
                    String.format("第 %d 轮辩论已结束，共完成 %d 次发言，请宣布本轮胜方。", currentSession.getCurrentRound(), currentSession.getCurrentTurn()),
                    buildNoticeExtData("ROUND_END", currentSession, roundEndMessage.getSenderId(), null, true, null)
            ));
            return null;
        }

        if (!chatRoomRepository.updateDebateSessionProgress(session)) {
            throw new DependencyConflictException("辩论进度已变更，请刷新后重试");
        }
        advanceSessionVersion(session);
        state.setActiveDebateSessionId(session.getSessionId());
        persistRoomDebateState(roomId, state);
        return arbitrateNextSpeaker(session, lastMessage);
    }

    private void ensureRoomExists(String roomId) {
        if (roomId == null || roomId.isBlank() || chatRoomRepository.queryRoomById(roomId) == null) {
            throw new MissingException("房间不存在");
        }
    }

    private void validateClientMember(String roomId, String clientId) {
        if (chatRoomRepository.queryClientMembersByIds(roomId, List.of(clientId)).isEmpty()) {
            throw new MissingException("指定成员不是当前房间中的 CLIENT");
        }
    }

    private List<String> normalizeDebaters(List<String> clientIds) {
        if (clientIds == null) {
            return new ArrayList<>();
        }
        return clientIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private void validateDebateSides(String roomId, String arbitratorId, List<String> proClientIds, List<String> conClientIds) {
        if (proClientIds.isEmpty() || conClientIds.isEmpty()) {
            throw new MissingException("正方与反方至少各选择一名辩手");
        }

        Set<String> roomClients = chatRoomRepository.queryClientsByRoomId(roomId).stream()
                .map(AiChatRoomMemberEntity::getMemberId)
                .collect(Collectors.toSet());
        Set<String> overlap = new LinkedHashSet<>(proClientIds);
        overlap.retainAll(conClientIds);
        if (!overlap.isEmpty()) {
            throw new DependencyConflictException("正反方成员不能重叠");
        }

        for (String clientId : proClientIds) {
            if (!roomClients.contains(clientId)) {
                throw new MissingException("正方成员不在当前房间中");
            }
            if (clientId.equals(arbitratorId)) {
                throw new DependencyConflictException("仲裁者不能同时参与辩论");
            }
        }
        for (String clientId : conClientIds) {
            if (!roomClients.contains(clientId)) {
                throw new MissingException("反方成员不在当前房间中");
            }
            if (clientId.equals(arbitratorId)) {
                throw new DependencyConflictException("仲裁者不能同时参与辩论");
            }
        }
    }

    private DebateSessionEntity getRequiredActiveSession(String roomId) {
        DebateSessionEntity session = chatRoomRepository.queryActiveDebateSession(roomId);
        if (session == null) {
            throw new MissingException("当前房间没有进行中的辩论");
        }
        return session;
    }

    private RoomDebateStateVO loadRoomDebateState(String roomId) {
        chatRoomRepository.initRoomStateIfAbsent(roomId);
        RoomDebateStateVO state = chatRoomRepository.queryRoomDebateState(roomId);
        if (state == null) {
            return RoomDebateStateVO.builder().version(0).build();
        }
        if (state.getVersion() == null) {
            state.setVersion(0);
        }
        return state;
    }

    private void persistRoomDebateState(String roomId, RoomDebateStateVO state) {
        Integer currentVersion = state.getVersion() == null ? 0 : state.getVersion();
        if (!chatRoomRepository.saveRoomDebateState(roomId, state, currentVersion)) {
            throw new DependencyConflictException("房间辩论状态已变更，请刷新后重试");
        }
        state.setVersion(currentVersion + 1);
    }

    private String buildSessionId() {
        return "debate_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String buildRecordId() {
        return "dr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String normalizeTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            throw new MissingException("辩题不能为空");
        }
        return topic.trim();
    }

    private int normalizeTurnsPerRound(Integer turnsPerRound) {
        if (turnsPerRound == null || turnsPerRound <= 0) {
            return DEFAULT_TURNS_PER_ROUND;
        }
        return turnsPerRound;
    }

    private String normalizeWinnerSide(String winnerSide) {
        if (winnerSide == null) {
            throw new MissingException("winnerSide 不能为空");
        }
        String normalized = winnerSide.trim().toUpperCase();
        if (!"PRO".equals(normalized) && !"CON".equals(normalized)) {
            throw new MissingException("winnerSide 只能是 PRO 或 CON");
        }
        return normalized;
    }

    private List<DebateMemberStatusVO> toMemberStatusVO(List<String> clientIds, Map<String, String> clientNameMap) {
        if (clientIds == null) {
            return new ArrayList<>();
        }
        return clientIds.stream()
                .map(clientId -> DebateMemberStatusVO.builder()
                        .clientId(clientId)
                        .clientName(clientNameMap.getOrDefault(clientId, clientId))
                        .build())
                .collect(Collectors.toList());
    }

    private Map<String, String> buildClientNameMap(String roomId, List<String> clientIds) {
        if (clientIds == null || clientIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return chatRoomRepository.queryClientMembersByIds(roomId, clientIds).stream()
                .collect(Collectors.toMap(AiChatRoomMemberEntity::getMemberId,
                        item -> item.getMemberName() == null || item.getMemberName().isBlank() ? item.getMemberId() : item.getMemberName(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Map<String, Integer> buildJoinOrderMap(String roomId, List<String> candidateIds) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Set<String> candidateSet = new LinkedHashSet<>(candidateIds);
        Map<String, Integer> joinOrderMap = new LinkedHashMap<>();
        int index = 1;
        for (AiChatRoomMemberEntity member : chatRoomRepository.queryClientsByRoomId(roomId)) {
            if (candidateSet.contains(member.getMemberId()) && !joinOrderMap.containsKey(member.getMemberId())) {
                joinOrderMap.put(member.getMemberId(), index++);
            }
        }
        for (String candidateId : candidateIds) {
            joinOrderMap.putIfAbsent(candidateId, index++);
        }
        return joinOrderMap;
    }

    private Map<String, Integer> buildSpeakerHistoryStats(List<DebateTurnRecordVO> roundHistory) {
        if (roundHistory == null || roundHistory.isEmpty()) {
            return new LinkedHashMap<>();
        }
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (DebateTurnRecordVO record : roundHistory) {
            if (record == null || record.getSpeakerId() == null) {
                continue;
            }
            stats.merge(record.getSpeakerId(), 1, Integer::sum);
        }
        return stats;
    }

    private Map<String, String> buildRoundWinnerMap(List<String> winners) {
        Map<String, String> winnerMap = new LinkedHashMap<>();
        if (winners == null || winners.isEmpty()) {
            return winnerMap;
        }
        for (int i = 0; i < winners.size(); i++) {
            winnerMap.put(String.valueOf(i + 1), winners.get(i));
        }
        return winnerMap;
    }

    private DispatchDecisionVO arbitrateNextSpeaker(DebateSessionEntity session, AiChatRoomMessageEntity triggerMessage) {
        ArbitrationPromptContextVO promptContext = buildArbitrationPromptContext(session, triggerMessage);
        if (promptContext.getCandidateSpeakerIds() == null || promptContext.getCandidateSpeakerIds().isEmpty()) {
            log.info("【ARBITRATOR_DECIDE】当前无合法候选可继续发言 roomId={}, sessionId={}", session.getRoomId(), session.getSessionId());
            return null;
        }

        ArbitrationDecisionResultVO result = debateArbitrationService.arbitrate(promptContext);
        if (!session.validateArbitrationResult(result.getSpeakerId(), promptContext.getLastSpeakerClientId())) {
            throw new DependencyConflictException("仲裁结果非法，未命中候选集");
        }

        log.info("【ARBITRATOR_DECIDE】仲裁完成 roomId={}, sessionId={}, round={}, turn={}, speakerId={}, source={}, candidates={}",
                session.getRoomId(),
                session.getSessionId(),
                session.getCurrentRound(),
                session.getCurrentTurn(),
                result.getSpeakerId(),
                result.getDecisionSource(),
                promptContext.getCandidateSpeakerIds());

        return DispatchDecisionVO.builder()
                .speakerId(result.getSpeakerId())
                .decisionSource(result.getDecisionSource())
                .reasoning(result.getReasoning())
                .sessionId(session.getSessionId())
                .roundNumber(session.getCurrentRound())
                .sessionVersion(session.getVersion())
                .build();
    }

    private ArbitrationPromptContextVO buildArbitrationPromptContext(DebateSessionEntity session, AiChatRoomMessageEntity triggerMessage) {
        DebateContextVO debateContext = chatRoomRepository.queryDebateContext(session.getSessionId());
        List<DebateTurnRecordVO> roundHistory = chatRoomRepository.queryDebateRecordsForPrompt(session.getSessionId(), session.getCurrentRound());
        List<AiChatRoomMessageEntity> recentConversation = new ArrayList<>(chatRoomRepository.queryContextMessages(session.getRoomId(), 12));
        Collections.reverse(recentConversation);

        List<String> allClientIds = new ArrayList<>();
        allClientIds.add(session.getArbitratorClientId());
        allClientIds.addAll(session.getAllDebaterIds());
        Map<String, String> clientNameMap = buildClientNameMap(session.getRoomId(), allClientIds);

        String lastSpeakerClientId = triggerMessage == null ? null : triggerMessage.getSenderId();
        String lastSpeakerName = triggerMessage == null ? null : triggerMessage.getSenderName();
        List<String> candidateSpeakerIds = session.listCandidateSpeakerIds(lastSpeakerClientId);
        Map<String, Integer> candidateJoinOrder = buildJoinOrderMap(session.getRoomId(), candidateSpeakerIds);
        List<String> preferredSpeakerIds = session.listPreferredSpeakerIds(lastSpeakerClientId, roundHistory, candidateJoinOrder);

        return ArbitrationPromptContextVO.builder()
                .roomId(session.getRoomId())
                .sessionId(session.getSessionId())
                .topic(debateContext == null ? session.getTopic() : debateContext.getTopic())
                .arbitratorClientId(session.getArbitratorClientId())
                .currentRound(session.getCurrentRound())
                .currentTurn(session.getCurrentTurn())
                .turnsPerRound(session.getTurnsPerRound())
                .lastSpeakerClientId(lastSpeakerClientId)
                .lastSpeakerName(lastSpeakerName)
                .lastSpeakerSide(session.getSideForClient(lastSpeakerClientId))
                .candidateSpeakerIds(candidateSpeakerIds)
                .preferredSpeakerIds(preferredSpeakerIds)
                .candidateJoinOrder(candidateJoinOrder)
                .speakerHistoryStats(buildSpeakerHistoryStats(roundHistory))
                .proMembers(toMemberStatusVO(session.getProClientIds(), clientNameMap))
                .conMembers(toMemberStatusVO(session.getConClientIds(), clientNameMap))
                .roundHistory(roundHistory)
                .recentConversation(recentConversation)
                .build();
    }

    private void saveDebateRecord(DebateSessionEntity session, String roomId, RoomDebateStateVO state, AiChatRoomMessageEntity lastMessage) {
        DebateRecordEntity record = DebateRecordEntity.builder()
                .recordId(buildRecordId())
                .sessionId(session.getSessionId())
                .roomId(roomId)
                .roundNumber(session.getCurrentRound())
                .turnNumber((session.getCurrentTurn() == null ? 0 : session.getCurrentTurn()) + 1)
                .speakerClientId(lastMessage.getSenderId())
                .speakerName(lastMessage.getSenderName())
                .side(session.getSideForClient(lastMessage.getSenderId()))
                .messageId(lastMessage.getMessageId())
                .arbitratorReasoning(buildRecordReasoning(state))
                .build();
        chatRoomRepository.saveDebateRecord(record);
    }

    private boolean isExpectedSpeaker(RoomDebateStateVO state, String speakerId) {
        if (speakerId == null || speakerId.isBlank()) {
            return false;
        }
        if (state == null || state.getPendingSpeakerId() == null || state.getPendingSpeakerId().isBlank()) {
            return true;
        }
        return Objects.equals(state.getPendingSpeakerId(), speakerId);
    }

    private String buildRecordReasoning(RoomDebateStateVO state) {
        if (state == null) {
            return "系统已完成本次辩手调度。";
        }
        String source = state.getPendingDecisionSource();
        String reasoning = state.getPendingDecisionReasoning();
        String summary = switch (source == null ? "" : source) {
            case "AT_MENTION" -> "用户指定优先回应";
            case "ROUND_ROBIN_FALLBACK" -> "仲裁降级轮转补位";
            case "PROBABILITY" -> "自由聊天随机命中";
            default -> "仲裁者已完成本次调度";
        };
        if (reasoning == null || reasoning.isBlank()) {
            return summary;
        }
        String result = summary + "：" + reasoning;
        return result.length() > RECORD_REASONING_LIMIT ? result.substring(0, RECORD_REASONING_LIMIT) : result;
    }

    private void clearPendingDispatch(RoomDebateStateVO state) {
        state.setPendingSpeakerId(null);
        state.setPendingTurnNumber(null);
        state.setPendingDecisionSource(null);
        state.setPendingDecisionReasoning(null);
        state.setDispatchSessionId(null);
        state.setDispatchRound(null);
        state.setDispatchVersion(null);
    }

    private void clearRoundState(RoomDebateStateVO state) {
        state.setPendingRoundNumber(null);
        state.setPendingRoundTurnCount(null);
        state.setLastRoundEndedAt(null);
    }

    private void advanceSessionVersion(DebateSessionEntity session) {
        session.setVersion((session.getVersion() == null ? 0 : session.getVersion()) + 1);
    }

    private void publishDispatchTrigger(String roomId) {
        roomEventPublisher.publishInternal(WebSocketEvent.builder()
                .roomId(roomId)
                .eventType(WebSocketEvent.EventType.DEBATE_DISPATCH_TRIGGER)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    private void runAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    private String buildNoticeExtData(String noticeType, DebateSessionEntity session, String speakerId, String reasoning, boolean waitingForWinner, String decisionSource) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noticeType", noticeType);
        data.put("sessionId", session.getSessionId());
        data.put("topic", session.getTopic());
        data.put("roundNumber", session.getCurrentRound());
        data.put("currentTurn", session.getCurrentTurn());
        data.put("turnsPerRound", session.getTurnsPerRound());
        data.put("speakerId", speakerId);
        data.put("reasoning", reasoning);
        data.put("waitingForWinner", waitingForWinner);
        data.put("decisionSource", decisionSource);
        return JSON.toJSONString(data);
    }

    private String buildWinnerExtData(DebateSessionEntity session, String winnerSide) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noticeType", "ROUND_WINNER");
        data.put("sessionId", session.getSessionId());
        data.put("roundNumber", session.getCurrentRound());
        data.put("winnerSide", winnerSide);
        data.put("roundWinners", buildRoundWinnerMap(session.safeRoundWinners()));
        return JSON.toJSONString(data);
    }

    private String buildSpeakerErrorExtData(DebateSessionEntity session, AiChatRoomMessageEntity lastMessage) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noticeType", "SPEAKER_ERROR");
        data.put("sessionId", session.getSessionId());
        data.put("roundNumber", session.getCurrentRound());
        data.put("currentTurn", session.getCurrentTurn());
        data.put("speakerId", lastMessage.getSenderId());
        data.put("speakerName", lastMessage.getSenderName());
        data.put("errorType", extractErrorType(lastMessage.getExtData()));
        data.put("errorMessage", extractErrorMessage(lastMessage.getExtData()));
        return JSON.toJSONString(data);
    }

    private String buildSimpleExtData(String noticeType, String roomId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noticeType", noticeType);
        data.put("roomId", roomId);
        return JSON.toJSONString(data);
    }

    private String extractErrorType(String extData) {
        try {
            return extData == null ? "EXECUTION_FAILED" : JSON.parseObject(extData).getString("errorType");
        } catch (Exception e) {
            return "EXECUTION_FAILED";
        }
    }

    private String extractErrorMessage(String extData) {
        try {
            return extData == null ? "执行失败" : JSON.parseObject(extData).getString("errorMessage");
        } catch (Exception e) {
            return "执行失败";
        }
    }
}
