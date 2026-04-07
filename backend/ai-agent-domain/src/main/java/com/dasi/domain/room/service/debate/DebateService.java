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
import com.dasi.domain.room.model.valobj.DebateRoundSummaryVO;
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
    private static final int ROOM_STATE_WRITE_MAX_RETRY = 3;
    private static final int SESSION_STATUS_WRITE_MAX_RETRY = 3;

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

        RoomDebateStateVO state = loadRoomDebateStateFresh(roomId);
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
        RoomDebateStateVO state = loadRoomDebateStateFresh(roomId);
        DebateSessionEntity session = resolveSessionForStop(roomId, state);
        finishSessionStrongly(roomId, session);

        state.setActiveDebateSessionId(null);
        clearRoundState(state);
        clearPendingDispatch(state);
        clearCurrentSlotState(state);
        state.setLastRoundSummary(null);
        if (!persistRoomDebateStateForce(roomId, state)) {
            throw new DependencyConflictException("房间辩论状态清理失败，请重试停止操作");
        }

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
                    .canDeclareWinner(Boolean.FALSE)
                    .canStartNextRound(Boolean.FALSE)
                    .canStopDebate(Boolean.FALSE)
                    .build();
        }

        List<String> allClientIds = new ArrayList<>();
        allClientIds.add(session.getArbitratorClientId());
        allClientIds.addAll(session.getAllDebaterIds());
        Map<String, String> nameMap = buildClientNameMap(roomId, allClientIds);
        boolean waitingForWinner = state.waitingForWinner();
        boolean hasCurrentRoundWinner = hasWinnerForRound(session, session.getCurrentRound());
        boolean canDeclareWinner = DebateStatus.ROUND_END.equals(session.getStatus()) && waitingForWinner;
        boolean canStartNextRound = DebateStatus.ROUND_END.equals(session.getStatus())
                && !waitingForWinner
                && hasCurrentRoundWinner;
        boolean canStopDebate = DebateStatus.RUNNING.equals(session.getStatus())
                || DebateStatus.ROUND_END.equals(session.getStatus());

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
                .waitingForWinner(waitingForWinner)
                .pendingRoundNumber(state.getPendingRoundNumber())
                .lastRoundSummary(state.getLastRoundSummary())
                .proMembers(toMemberStatusVO(session.getProClientIds(), nameMap))
                .conMembers(toMemberStatusVO(session.getConClientIds(), nameMap))
                .canDeclareWinner(canDeclareWinner)
                .canStartNextRound(canStartNextRound)
                .canStopDebate(canStopDebate)
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
            clearCurrentSlotState(state);
            clearPendingDispatch(state);
            state.setActiveDebateSessionId(session.getSessionId());
            persistRoomDebateState(roomId, state);
            return arbitrateNextSpeaker(session, state, null);
        }

        AiChatRoomMessageEntity lastMessage = strategyEntity.getCurrentMessage();
        if (lastMessage == null || !session.validateDebater(lastMessage.getSenderId())) {
            log.info("【ARBITRATOR_DECIDE】忽略非辩手或空消息 roomId={}, eventType={}", roomId, eventType);
            return null;
        }
        if (!matchesDispatchGuard(state, session, strategyEntity, lastMessage)) {
            log.info("【STALE_CALLBACK_DROPPED】忽略过期或越权辩手回流 roomId={}, sessionId={}, senderId={}, pendingSpeakerId={}, traceId={}",
                    roomId,
                    session.getSessionId(),
                    lastMessage.getSenderId(),
                    state.getPendingSpeakerId(),
                    strategyEntity.getTraceId());
            return null;
        }

        if (WebSocketEvent.EventType.CLIENT_MSG_END.equals(eventType) || WebSocketEvent.EventType.CLIENT_MSG_ERROR.equals(eventType)) {
            String eventTraceId = strategyEntity.getTraceId();
            if (eventTraceId == null || eventTraceId.isBlank()) {
                eventTraceId = strategyEntity.getCurrentMessage() != null ? strategyEntity.getCurrentMessage().getTraceId() : null;
            }
            if (eventTraceId == null || !Objects.equals(eventTraceId, state.getDispatchTraceId())) {
                log.info("【STALE_CALLBACK_DROPPED】丢弃过期回调 roomId={}, eventTraceId={}, currentTraceId={}",
                        roomId, eventTraceId, state.getDispatchTraceId());
                return null;
            }
        }

        if (WebSocketEvent.EventType.CLIENT_MSG_END.equals(eventType)) {
            saveDebateRecord(session, roomId, state, lastMessage);
            session.recordTurnFinished();
            clearPendingDispatch(state);
            clearCurrentSlotState(state);
            return finishTurnAndDispatchNext(roomId, session, state, lastMessage);
        }

        String failedSide = session.getSideForClient(lastMessage.getSenderId());
        markSlotAttempt(state, failedSide, lastMessage.getSenderId());
        clearPendingDispatch(state);
        state.setActiveDebateSessionId(session.getSessionId());

        final DebateSessionEntity currentSession = session;
        final AiChatRoomMessageEntity failedMessage = lastMessage;
        final int retryCount = state.getSlotRetryCount() == null ? 0 : state.getSlotRetryCount();
        runAfterCommit(() -> roomChatService.publishSystemNotice(
                roomId,
                "SPEAKER_ERROR",
                String.format("辩手 %s 本次发言失败，仲裁者正在为%s重新选择发言者。", failedMessage.getSenderName(), formatSideName(failedSide)),
                buildSpeakerErrorExtData(currentSession, failedMessage, retryCount)
        ));

        DispatchDecisionVO retryDecision = arbitrateNextSpeaker(session, state, lastMessage);
        if (retryDecision != null) {
            log.info("【SLOT_RETRY】同槽补位 roomId={}, sessionId={}, round={}, turn={}, requiredSide={}, retryCount={}, nextSpeakerId={}",
                    roomId,
                    session.getSessionId(),
                    session.getCurrentRound(),
                    session.getCurrentTurn(),
                    state.getSlotRequiredSide(),
                    state.getSlotRetryCount(),
                    retryDecision.getSpeakerId());
            return retryDecision;
        }

        final List<String> attemptedSpeakerIds = state.getSlotAttemptedSpeakerIds() == null
                ? List.of()
                : new ArrayList<>(state.getSlotAttemptedSpeakerIds());
        final String requiredSide = state.getSlotRequiredSide();
        clearCurrentSlotState(state);
        session.recordTurnFinished();
        runAfterCommit(() -> roomChatService.publishSystemNotice(
                roomId,
                "SLOT_SKIPPED",
                String.format("%s本次发言位补位失败，系统将跳过这一槽位并继续下一次攻防。", formatSideName(requiredSide)),
                buildSlotSkippedExtData(currentSession, requiredSide, attemptedSpeakerIds)
        ));

        log.info("【SLOT_SKIPPED】同槽补位耗尽 roomId={}, sessionId={}, round={}, turn={}, requiredSide={}, attemptedSpeakerIds={}",
                roomId,
                session.getSessionId(),
                session.getCurrentRound(),
                session.getCurrentTurn(),
                requiredSide,
                attemptedSpeakerIds);

        return finishTurnAndDispatchNext(roomId, session, state, buildVirtualTriggerMessage(lastMessage));
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
        return ensureStateVersion(chatRoomRepository.queryRoomDebateState(roomId));
    }

    private RoomDebateStateVO loadRoomDebateStateFresh(String roomId) {
        chatRoomRepository.initRoomStateIfAbsent(roomId);
        return ensureStateVersion(chatRoomRepository.queryRoomDebateStateFresh(roomId));
    }

    private void persistRoomDebateState(String roomId, RoomDebateStateVO state) {
        RoomDebateStateVO targetState = state == null ? RoomDebateStateVO.builder().version(0).build() : state;
        for (int attempt = 1; attempt <= ROOM_STATE_WRITE_MAX_RETRY; attempt++) {
            Integer currentVersion = targetState.getVersion() == null ? 0 : targetState.getVersion();
            if (chatRoomRepository.saveRoomDebateState(roomId, targetState, currentVersion)) {
                targetState.setVersion(currentVersion + 1);
                return;
            }
            RoomDebateStateVO freshState = loadRoomDebateStateFresh(roomId);
            targetState.setVersion(freshState.getVersion());
            log.warn("【DEBATE_STATE_RETRY】房间状态写入冲突，重试 roomId={}, attempt={}, freshVersion={}",
                    roomId, attempt, freshState.getVersion());
        }
        throw new DependencyConflictException("房间辩论状态已变更，请刷新后重试");
    }

    private boolean persistRoomDebateStateForce(String roomId, RoomDebateStateVO state) {
        RoomDebateStateVO targetState = state == null ? RoomDebateStateVO.builder().version(0).build() : state;
        boolean saved = chatRoomRepository.forceSaveRoomDebateState(roomId, targetState);
        if (saved) {
            Integer currentVersion = targetState.getVersion() == null ? 0 : targetState.getVersion();
            targetState.setVersion(currentVersion + 1);
            return true;
        }
        return false;
    }

    private RoomDebateStateVO ensureStateVersion(RoomDebateStateVO state) {
        if (state == null) {
            return RoomDebateStateVO.builder().version(0).build();
        }
        if (state.getVersion() == null) {
            state.setVersion(0);
        }
        return state;
    }

    private DebateSessionEntity resolveSessionForStop(String roomId, RoomDebateStateVO state) {
        if (state != null && state.getActiveDebateSessionId() != null && !state.getActiveDebateSessionId().isBlank()) {
            DebateSessionEntity session = chatRoomRepository.queryDebateSessionBySessionId(state.getActiveDebateSessionId());
            if (session != null) {
                return session;
            }
        }
        return chatRoomRepository.queryActiveDebateSession(roomId);
    }

    private void finishSessionStrongly(String roomId, DebateSessionEntity session) {
        if (session == null) {
            return;
        }
        String sessionId = session.getSessionId();
        for (int attempt = 1; attempt <= SESSION_STATUS_WRITE_MAX_RETRY; attempt++) {
            DebateSessionEntity latest = chatRoomRepository.queryDebateSessionBySessionId(sessionId);
            if (latest == null || latest.isFinished()) {
                return;
            }
            latest.finishDebate();
            if (chatRoomRepository.updateDebateSessionStatus(latest)) {
                advanceSessionVersion(latest);
                return;
            }
            log.warn("【DEBATE_STOP_FORCE】会话终止写入冲突，重试 roomId={}, sessionId={}, attempt={}",
                    roomId, sessionId, attempt);
        }
        DebateSessionEntity latest = chatRoomRepository.queryDebateSessionBySessionId(sessionId);
        if (latest != null && !latest.isFinished()) {
            latest.finishDebate();
            if (chatRoomRepository.forceUpdateDebateSessionStatus(latest)) {
                log.warn("【DEBATE_STOP_FORCE】已执行会话状态强制终止 roomId={}, sessionId={}", roomId, sessionId);
                return;
            }
        }
        throw new DependencyConflictException("辩论状态已变更，请重试停止操作");
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

    private boolean hasWinnerForRound(DebateSessionEntity session, Integer roundNumber) {
        if (session == null || roundNumber == null || roundNumber <= 0) {
            return false;
        }
        List<String> winners = session.safeRoundWinners();
        return winners != null && winners.size() >= roundNumber;
    }

    /**
     * finishTurnAndDispatchNext 负责“一个逻辑发言槽已结束”后的统一收口：
     * 成功发言或显式跳过都会先落本轮进度，再决定是进入 ROUND_END 还是生成下一位 speaker 决策。
     */
    private DispatchDecisionVO finishTurnAndDispatchNext(String roomId,
                                                         DebateSessionEntity session,
                                                         RoomDebateStateVO state,
                                                         AiChatRoomMessageEntity triggerMessage) {
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
            state.setLastRoundSummary(buildRoundSummary(session, triggerMessage));
            persistRoomDebateState(roomId, state);

            final DebateSessionEntity currentSession = session;
            final AiChatRoomMessageEntity roundEndMessage = triggerMessage;
            runAfterCommit(() -> roomChatService.publishSystemNotice(
                    roomId,
                    "ROUND_END",
                    String.format("第 %d 轮辩论已结束，共完成 %d 次发言，请宣布本轮胜方。", currentSession.getCurrentRound(), currentSession.getCurrentTurn()),
                    buildNoticeExtData("ROUND_END", currentSession, roundEndMessage == null ? null : roundEndMessage.getSenderId(), null, true, null)
            ));
            return null;
        }

        if (!chatRoomRepository.updateDebateSessionProgress(session)) {
            throw new DependencyConflictException("辩论进度已变更，请刷新后重试");
        }
        advanceSessionVersion(session);
        return arbitrateNextSpeaker(session, state, triggerMessage);
    }

    private DispatchDecisionVO arbitrateNextSpeaker(DebateSessionEntity session,
                                                    RoomDebateStateVO state,
                                                    AiChatRoomMessageEntity triggerMessage) {
        ArbitrationPromptContextVO promptContext = buildArbitrationPromptContext(session, state, triggerMessage);
        log.info("【ARBITRATOR_PROMPT_CONTEXT】sessionId={}, roomId={}, arbitratorClientId={}, round={}, turn={}, triggerSpeakerId={}, requiredSide={}, excluded={}, candidates={}, preferred={}",
                session.getSessionId(),
                session.getRoomId(),
                promptContext.getArbitratorClientId(),
                session.getCurrentRound(),
                session.getCurrentTurn(),
                triggerMessage == null ? null : triggerMessage.getSenderId(),
                promptContext.getRequiredSide(),
                promptContext.getExcludedSpeakerIds(),
                promptContext.getCandidateSpeakerIds(),
                promptContext.getPreferredSpeakerIds());
        if (promptContext.getCandidateSpeakerIds() == null || promptContext.getCandidateSpeakerIds().isEmpty()) {
            log.info("【ARBITRATOR_DECIDE】当前无合法候选可继续发言 roomId={}, sessionId={}, requiredSide={}, excludedSpeakerIds={}",
                    session.getRoomId(),
                    session.getSessionId(),
                    promptContext.getRequiredSide(),
                    promptContext.getExcludedSpeakerIds());
            return null;
        }

        ArbitrationDecisionResultVO result = debateArbitrationService.arbitrate(promptContext);
        Set<String> excludedSpeakerIds = promptContext.getExcludedSpeakerIds() == null
                ? Collections.emptySet()
                : new LinkedHashSet<>(promptContext.getExcludedSpeakerIds());
        if (!session.validateArbitrationResult(result.getSpeakerId(),
                promptContext.getLastSpeakerClientId(),
                promptContext.getRequiredSide(),
                excludedSpeakerIds)) {
            throw new DependencyConflictException("仲裁结果非法，未命中候选集");
        }

        String dispatchTraceId = buildDispatchTraceId(session);
        log.info("【ARBITRATOR_DECIDE】仲裁完成 roomId={}, sessionId={}, round={}, turn={}, speakerId={}, source={}, requiredSide={}, candidates={}, preferred={}, traceId={}",
                session.getRoomId(),
                session.getSessionId(),
                session.getCurrentRound(),
                session.getCurrentTurn(),
                result.getSpeakerId(),
                result.getDecisionSource(),
                promptContext.getRequiredSide(),
                promptContext.getCandidateSpeakerIds(),
                promptContext.getPreferredSpeakerIds(),
                dispatchTraceId);

        DispatchDecisionVO decision = DispatchDecisionVO.builder()
                .speakerId(result.getSpeakerId())
                .decisionSource(result.getDecisionSource())
                .reasoning(result.getReasoning())
                .sessionId(session.getSessionId())
                .roundNumber(session.getCurrentRound())
                .sessionVersion(session.getVersion())
                .dispatchTraceId(dispatchTraceId)
                .requiredSide(promptContext.getRequiredSide())
                .build();
        applyPendingDispatchState(state, session, decision);
        persistRoomDebateState(session.getRoomId(), state);
        log.info("【FIRST_DISPATCH_READY】写入待执行护栏 roomId={}, sessionId={}, speakerId={}, traceId={}, round={}, turn={}",
                session.getRoomId(),
                session.getSessionId(),
                decision.getSpeakerId(),
                decision.getDispatchTraceId(),
                session.getCurrentRound(),
                session.getCurrentTurn());
        return decision;
    }

    private ArbitrationPromptContextVO buildArbitrationPromptContext(DebateSessionEntity session,
                                                                     RoomDebateStateVO state,
                                                                     AiChatRoomMessageEntity triggerMessage) {
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
        Set<String> excludedSpeakerIds = safeSlotExcludedIds(state);
        String requiredSide = state == null ? null : state.getSlotRequiredSide();
        List<String> candidateSpeakerIds = session.listCandidateSpeakerIds(lastSpeakerClientId, requiredSide, excludedSpeakerIds);
        Map<String, Integer> candidateJoinOrder = buildJoinOrderMap(session.getRoomId(), candidateSpeakerIds);
        List<String> preferredSpeakerIds = session.listPreferredSpeakerIds(lastSpeakerClientId, requiredSide, excludedSpeakerIds, roundHistory, candidateJoinOrder);

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
                .requiredSide(requiredSide)
                .excludedSpeakerIds(new ArrayList<>(excludedSpeakerIds))
                .slotRetryCount(state == null || state.getSlotRetryCount() == null ? 0 : state.getSlotRetryCount())
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

    private boolean matchesDispatchGuard(RoomDebateStateVO state,
                                         DebateSessionEntity session,
                                         DispatchStrategyEntity strategyEntity,
                                         AiChatRoomMessageEntity message) {
        if (state == null || session == null || strategyEntity == null || message == null) {
            logGuardReject("INVALID_INPUT", state, session, strategyEntity, message);
            return false;
        }
        if (!Objects.equals(state.getActiveDebateSessionId(), session.getSessionId())) {
            logGuardReject("SESSION_MISMATCH", state, session, strategyEntity, message);
            return false;
        }
        if (state.getPendingSpeakerId() == null || state.getPendingSpeakerId().isBlank()) {
            logGuardReject("PENDING_EMPTY", state, session, strategyEntity, message);
            return false;
        }
        if (!Objects.equals(state.getPendingSpeakerId(), message.getSenderId())) {
            logGuardReject("SPEAKER_MISMATCH", state, session, strategyEntity, message);
            return false;
        }

        String strategyTraceId = strategyEntity.getTraceId();
        if (strategyTraceId == null || strategyTraceId.isBlank()) {
            logGuardReject("TRACE_MISSING", state, session, strategyEntity, message);
            return false;
        }
        if (!Objects.equals(state.getDispatchTraceId(), strategyTraceId)) {
            logGuardReject("TRACE_MISMATCH", state, session, strategyEntity, message);
            return false;
        }

        String extTraceId = extractExtDataValue(message, "dispatchTraceId");
        String extSessionId = extractExtDataValue(message, "dispatchSessionId");
        String extRoundRaw = extractExtDataValue(message, "dispatchRound");
        String extVersionRaw = extractExtDataValue(message, "dispatchVersion");
        Integer extRound = parseInteger(extRoundRaw);
        Integer extVersion = parseInteger(extVersionRaw);

        boolean hasExtTrace = hasText(extTraceId);
        boolean hasExtSession = hasText(extSessionId);
        boolean hasExtRound = hasText(extRoundRaw);
        boolean hasExtVersion = hasText(extVersionRaw);
        int extPresentCount = (hasExtTrace ? 1 : 0) + (hasExtSession ? 1 : 0) + (hasExtRound ? 1 : 0) + (hasExtVersion ? 1 : 0);

        if (extPresentCount == 4) {
            if (!Objects.equals(extTraceId, strategyTraceId)) {
                logGuardReject("TRACE_MISMATCH", state, session, strategyEntity, message);
                return false;
            }
            if (!Objects.equals(extSessionId, session.getSessionId())) {
                logGuardReject("SESSION_MISMATCH", state, session, strategyEntity, message);
                return false;
            }
            if (!Objects.equals(extRound, state.getDispatchRound())) {
                logGuardReject("ROUND_MISMATCH", state, session, strategyEntity, message);
                return false;
            }
            if (!Objects.equals(extVersion, state.getDispatchVersion())) {
                logGuardReject("VERSION_MISMATCH", state, session, strategyEntity, message);
                return false;
            }
            log.info("【DEBATE_GUARD_STRICT_PASS】roomId={}, sessionId={}, senderId={}, traceId={}, round={}, version={}",
                    session.getRoomId(), session.getSessionId(), message.getSenderId(), strategyTraceId, state.getDispatchRound(), state.getDispatchVersion());
            return true;
        }

        if (extPresentCount > 0) {
            logGuardReject("EXTDATA_INCOMPLETE", state, session, strategyEntity, message);
            return false;
        }

        // 兼容回退：历史成功回流可能没有 extData，但 trace + state + pending 一致时允许继续推进。
        String eventTraceId = resolveEventTraceId(strategyEntity, message);
        if (eventTraceId == null || eventTraceId.isBlank() || !Objects.equals(state.getDispatchTraceId(), eventTraceId)) {
            logGuardReject("TRACE_MISMATCH", state, session, strategyEntity, message);
            return false;
        }
        if (!Objects.equals(state.getDispatchSessionId(), session.getSessionId())) {
            logGuardReject("SESSION_MISMATCH", state, session, strategyEntity, message);
            return false;
        }
        if (!Objects.equals(state.getDispatchRound(), session.getCurrentRound())) {
            logGuardReject("ROUND_MISMATCH", state, session, strategyEntity, message);
            return false;
        }
        if (!Objects.equals(state.getDispatchVersion(), session.getVersion())) {
            logGuardReject("VERSION_MISMATCH", state, session, strategyEntity, message);
            return false;
        }

        log.info("【DEBATE_GUARD_FALLBACK_PASS】roomId={}, sessionId={}, senderId={}, traceId={}, round={}, version={}",
                session.getRoomId(), session.getSessionId(), message.getSenderId(), eventTraceId, state.getDispatchRound(), state.getDispatchVersion());
        return true;
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
        state.setDispatchTraceId(null);
    }

    private void applyPendingDispatchState(RoomDebateStateVO state,
                                           DebateSessionEntity session,
                                           DispatchDecisionVO decision) {
        state.setActiveDebateSessionId(session.getSessionId());
        state.setPendingSpeakerId(decision.getSpeakerId());
        state.setPendingTurnNumber((session.getCurrentTurn() == null ? 0 : session.getCurrentTurn()) + 1);
        state.setPendingDecisionSource(decision.getDecisionSource());
        state.setPendingDecisionReasoning(decision.getReasoning());
        state.setDispatchSessionId(session.getSessionId());
        state.setDispatchRound(session.getCurrentRound());
        state.setDispatchVersion(session.getVersion());
        state.setDispatchTraceId(decision.getDispatchTraceId());
        if (decision.getRequiredSide() != null && !decision.getRequiredSide().isBlank()) {
            state.setSlotRequiredSide(decision.getRequiredSide());
        }
    }

    private void clearCurrentSlotState(RoomDebateStateVO state) {
        state.setSlotRequiredSide(null);
        state.setSlotAttemptedSpeakerIds(new ArrayList<>());
        state.setSlotRetryCount(0);
    }

    /**
     * 同槽重选只记录“当前槽已经尝试失败过谁”，不推进 turn。
     * 只有收到成功发言的 CLIENT_MSG_END，槽位才会被视为真正完成。
     */
    private void markSlotAttempt(RoomDebateStateVO state, String requiredSide, String failedSpeakerId) {
        if (requiredSide != null && !requiredSide.isBlank()) {
            state.setSlotRequiredSide(requiredSide);
        }
        if (state.getSlotAttemptedSpeakerIds() == null) {
            state.setSlotAttemptedSpeakerIds(new ArrayList<>());
        }
        if (failedSpeakerId != null && !failedSpeakerId.isBlank()
                && !state.getSlotAttemptedSpeakerIds().contains(failedSpeakerId)) {
            state.getSlotAttemptedSpeakerIds().add(failedSpeakerId);
        }
        state.setSlotRetryCount((state.getSlotRetryCount() == null ? 0 : state.getSlotRetryCount()) + 1);
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

    private String buildSpeakerErrorExtData(DebateSessionEntity session, AiChatRoomMessageEntity lastMessage, int retryCount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noticeType", "SPEAKER_ERROR");
        data.put("sessionId", session.getSessionId());
        data.put("roundNumber", session.getCurrentRound());
        data.put("currentTurn", session.getCurrentTurn());
        data.put("speakerId", lastMessage.getSenderId());
        data.put("speakerName", lastMessage.getSenderName());
        data.put("speakerSide", session.getSideForClient(lastMessage.getSenderId()));
        data.put("retryCount", retryCount);
        data.put("errorType", extractErrorType(lastMessage.getExtData()));
        data.put("errorMessage", extractErrorMessage(lastMessage.getExtData()));
        return JSON.toJSONString(data);
    }

    private String buildSlotSkippedExtData(DebateSessionEntity session, String requiredSide, List<String> attemptedSpeakerIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noticeType", "SLOT_SKIPPED");
        data.put("sessionId", session.getSessionId());
        data.put("roundNumber", session.getCurrentRound());
        data.put("currentTurn", session.getCurrentTurn());
        data.put("requiredSide", requiredSide);
        data.put("attemptedSpeakerIds", attemptedSpeakerIds);
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

    private DebateRoundSummaryVO buildRoundSummary(DebateSessionEntity session, AiChatRoomMessageEntity triggerMessage) {
        if (triggerMessage != null && triggerMessage.getContent() != null && !triggerMessage.getContent().isBlank()) {
            return session.buildRoundSummary(triggerMessage.getSenderId(), triggerMessage.getSenderName());
        }
        List<DebateRecordEntity> records = chatRoomRepository.queryDebateRecordsByRound(session.getSessionId(), session.getCurrentRound());
        if (records == null || records.isEmpty()) {
            return session.buildRoundSummary(null, null);
        }
        DebateRecordEntity latest = records.get(records.size() - 1);
        return session.buildRoundSummary(latest.getSpeakerClientId(), latest.getSpeakerName());
    }

    private String buildDispatchTraceId(DebateSessionEntity session) {
        return "trace_" + session.getSessionId() + "_" + session.getCurrentRound() + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private Set<String> safeSlotExcludedIds(RoomDebateStateVO state) {
        if (state == null || state.getSlotAttemptedSpeakerIds() == null || state.getSlotAttemptedSpeakerIds().isEmpty()) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(state.getSlotAttemptedSpeakerIds());
    }

    private AiChatRoomMessageEntity buildVirtualTriggerMessage(AiChatRoomMessageEntity failedMessage) {
        if (failedMessage == null) {
            return null;
        }
        return AiChatRoomMessageEntity.builder()
                .roomId(failedMessage.getRoomId())
                .senderId(failedMessage.getSenderId())
                .senderName(failedMessage.getSenderName())
                .senderType(failedMessage.getSenderType())
                .messageRole(failedMessage.getMessageRole())
                .content("")
                .extData(failedMessage.getExtData())
                .build();
    }

    private String extractExtDataValue(AiChatRoomMessageEntity message, String key) {
        try {
            if (message == null || message.getExtData() == null || message.getExtData().isBlank()) {
                return null;
            }
            return JSON.parseObject(message.getExtData()).getString(key);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String formatSideName(String side) {
        return "PRO".equals(side) ? "正方" : "CON".equals(side) ? "反方" : "当前阵营";
    }

    private String resolveEventTraceId(DispatchStrategyEntity strategyEntity, AiChatRoomMessageEntity message) {
        if (strategyEntity != null && hasText(strategyEntity.getTraceId())) {
            return strategyEntity.getTraceId();
        }
        if (message != null && hasText(message.getTraceId())) {
            return message.getTraceId();
        }
        return extractExtDataValue(message, "dispatchTraceId");
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private void logGuardReject(String reason,
                                RoomDebateStateVO state,
                                DebateSessionEntity session,
                                DispatchStrategyEntity strategyEntity,
                                AiChatRoomMessageEntity message) {
        log.info("【DEBATE_GUARD_REJECT】reason={}, roomId={}, sessionId={}, senderId={}, pendingSpeakerId={}, strategyTraceId={}, messageTraceId={}, dispatchTraceId={}",
                reason,
                session == null ? null : session.getRoomId(),
                session == null ? null : session.getSessionId(),
                message == null ? null : message.getSenderId(),
                state == null ? null : state.getPendingSpeakerId(),
                strategyEntity == null ? null : strategyEntity.getTraceId(),
                message == null ? null : message.getTraceId(),
                state == null ? null : state.getDispatchTraceId());
    }
}
