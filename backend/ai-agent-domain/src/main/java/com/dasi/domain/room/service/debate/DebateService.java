package com.dasi.domain.room.service.debate;

import com.alibaba.fastjson2.JSON;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.model.entity.DebateRecordEntity;
import com.dasi.domain.room.model.entity.DebateSessionEntity;
import com.dasi.domain.room.model.valobj.DebateMemberStatusVO;
import com.dasi.domain.room.model.valobj.DebateRoundSummaryVO;
import com.dasi.domain.room.model.valobj.DebateStatus;
import com.dasi.domain.room.model.valobj.DebateStatusVO;
import com.dasi.domain.room.model.valobj.DispatchDecisionVO;
import com.dasi.domain.room.model.valobj.RoomDebateStateVO;
import com.dasi.domain.room.service.IRoomChatService;
import com.dasi.domain.room.model.valobj.WebSocketEvent;
import com.dasi.types.exception.DependencyConflictException;
import com.dasi.types.exception.MissingException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
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
    private static final String SOURCE_DEBATE_ARBITRATOR = "DEBATE_ARBITRATOR";

    @Resource
    private IChatRoomRepository chatRoomRepository;

    @Resource
    private IRoomChatService roomChatService;

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
        state.setPendingRoundNumber(null);
        state.setPendingRoundTurnCount(null);
        state.setLastRoundEndedAt(null);
        state.setLastRoundSummary(null);
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
        state.setPendingRoundNumber(null);
        state.setPendingRoundTurnCount(null);
        state.setLastRoundEndedAt(null);
        state.setLastRoundSummary(null);
        persistRoomDebateState(roomId, state);

        roomChatService.publishSystemNotice(
                roomId,
                "DEBATE_START",
                String.format("辩论开始：%s。第 %d 轮开始，正方先发言。", session.getTopic(), session.getCurrentRound()),
                buildNoticeExtData("DEBATE_START", session, null, null, true)
        );

        String firstSpeakerId = session.nextSpeakerByRoundRobin(null);
        if (firstSpeakerId != null) {
            String reasoning = buildHostReasoning(session, firstSpeakerId);
            roomChatService.publishSystemNotice(
                    roomId,
                    "HOST_INTRO",
                    reasoning,
                    buildNoticeExtData("HOST_INTRO", session, firstSpeakerId, reasoning, true)
            );
            roomChatService.clientChat(roomId, firstSpeakerId);
        }

        return session.getSessionId();
    }

    @Override
    @Transactional
    public void declareRoundWinner(String roomId, String winnerSide) {
        DebateSessionEntity session = getRequiredActiveSession(roomId);
        session.declareRoundWinner(normalizeWinnerSide(winnerSide));
        if (!chatRoomRepository.updateDebateSessionStatus(session)) {
            throw new DependencyConflictException("辩论状态已变更，请刷新后重试");
        }

        RoomDebateStateVO state = loadRoomDebateState(roomId);
        state.setPendingRoundNumber(null);
        state.setPendingRoundTurnCount(null);
        if (state.getLastRoundSummary() != null) {
            state.getLastRoundSummary().setWaitingForWinner(Boolean.FALSE);
        }
        persistRoomDebateState(roomId, state);

        roomChatService.publishSystemNotice(
                roomId,
                "ROUND_WINNER",
                String.format("第 %d 轮胜方：%s。", session.getCurrentRound(), "PRO".equals(winnerSide) ? "正方" : "反方"),
                buildWinnerExtData(session, winnerSide)
        );
    }

    @Override
    @Transactional
    public void startNextRound(String roomId) {
        DebateSessionEntity session = getRequiredActiveSession(roomId);
        session.startNextRound();
        if (!chatRoomRepository.updateDebateSessionStatus(session)) {
            throw new DependencyConflictException("辩论状态已变更，请刷新后重试");
        }

        RoomDebateStateVO state = loadRoomDebateState(roomId);
        state.setPendingRoundNumber(null);
        state.setPendingRoundTurnCount(null);
        if (state.getLastRoundSummary() != null) {
            state.getLastRoundSummary().setWaitingForWinner(Boolean.FALSE);
        }
        persistRoomDebateState(roomId, state);

        roomChatService.publishSystemNotice(
                roomId,
                "ROUND_NEXT",
                String.format("第 %d 轮开始。", session.getCurrentRound()),
                buildNoticeExtData("ROUND_NEXT", session, null, null, false)
        );

        String speakerId = session.nextSpeakerByRoundRobin(null);
        if (speakerId != null) {
            String reasoning = buildHostReasoning(session, speakerId);
            roomChatService.publishSystemNotice(
                    roomId,
                    "HOST_INTRO",
                    reasoning,
                    buildNoticeExtData("HOST_INTRO", session, speakerId, reasoning, false)
            );
            roomChatService.clientChat(roomId, speakerId);
        }
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
        }

        state.setActiveDebateSessionId(null);
        state.setPendingRoundNumber(null);
        state.setPendingRoundTurnCount(null);
        state.setLastRoundEndedAt(null);
        if (state.getLastRoundSummary() != null) {
            state.getLastRoundSummary().setWaitingForWinner(Boolean.FALSE);
        }
        persistRoomDebateState(roomId, state);

        roomChatService.publishSystemNotice(
                roomId,
                "DEBATE_STOP",
                "辩论已结束，房间恢复自由聊天。",
                buildSimpleExtData("DEBATE_STOP", roomId)
        );
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
            return DebateStatusVO.builder()
                    .arbitratorClientId(state.getArbitratorClientId())
                    .arbitratorName(state.getArbitratorClientId() == null ? null : chatRoomRepository.queryMemberName(roomId, state.getArbitratorClientId()))
                    .waitingForWinner(state.waitingForWinner())
                    .pendingRoundNumber(state.getPendingRoundNumber())
                    .lastRoundSummary(state.getLastRoundSummary())
                    .roundWinners(new ArrayList<>())
                    .proMembers(new ArrayList<>())
                    .conMembers(new ArrayList<>())
                    .build();
        }

        return DebateStatusVO.builder()
                .sessionId(session.getSessionId())
                .topic(session.getTopic())
                .status(session.getStatus())
                .currentRound(session.getCurrentRound())
                .currentTurn(session.getCurrentTurn())
                .turnsPerRound(session.getTurnsPerRound())
                .roundWinners(new ArrayList<>(session.safeRoundWinners()))
                .arbitratorClientId(session.getArbitratorClientId())
                .arbitratorName(chatRoomRepository.queryMemberName(roomId, session.getArbitratorClientId()))
                .waitingForWinner(state.waitingForWinner())
                .pendingRoundNumber(state.getPendingRoundNumber())
                .lastRoundSummary(state.getLastRoundSummary())
                .proMembers(toMemberStatusVO(session.getProClientIds(), roomId))
                .conMembers(toMemberStatusVO(session.getConClientIds(), roomId))
                .build();
    }

    @Override
    @Transactional
    public DispatchDecisionVO handleDebateDispatch(String roomId, String eventType, AiChatRoomMessageEntity lastMessage) {
        if (!WebSocketEvent.EventType.CLIENT_MSG_END.equals(eventType)) {
            return null;
        }

        DebateSessionEntity session = chatRoomRepository.queryActiveDebateSession(roomId);
        if (session == null || !session.isRunning() || lastMessage == null) {
            return null;
        }
        if (!session.validateDebater(lastMessage.getSenderId())) {
            log.info("【辩论调度】忽略非辩手消息 roomId={}, senderId={}", roomId, lastMessage.getSenderId());
            return null;
        }

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
                .arbitratorReasoning("阶段3确定性轮转调度")
                .build();
        chatRoomRepository.saveDebateRecord(record);

        session.recordTurnFinished();
        if (session.isRoundComplete()) {
            session.finishCurrentRound();
            if (!chatRoomRepository.updateDebateSessionStatus(session)) {
                throw new DependencyConflictException("辩论状态已变更，请刷新后重试");
            }

            RoomDebateStateVO state = loadRoomDebateState(roomId);
            state.setActiveDebateSessionId(session.getSessionId());
            state.setPendingRoundNumber(session.getCurrentRound());
            state.setPendingRoundTurnCount(session.getCurrentTurn());
            state.setLastRoundEndedAt(Instant.now().toEpochMilli());
            state.setLastRoundSummary(session.buildRoundSummary(lastMessage.getSenderId(), lastMessage.getSenderName()));
            persistRoomDebateState(roomId, state);

            roomChatService.publishSystemNotice(
                    roomId,
                    "ROUND_END",
                    String.format("第 %d 轮辩论已结束，共完成 %d 次发言，请宣布本轮胜方。", session.getCurrentRound(), session.getCurrentTurn()),
                    buildNoticeExtData("ROUND_END", session, lastMessage.getSenderId(), null, true)
            );
            return null;
        }

        if (!chatRoomRepository.updateDebateSessionProgress(session)) {
            throw new DependencyConflictException("辩论进度已变更，请刷新后重试");
        }

        String nextSpeakerId = session.nextSpeakerByRoundRobin(lastMessage.getSenderId());
        if (nextSpeakerId == null) {
            return null;
        }

        String reasoning = buildHostReasoning(session, nextSpeakerId);
        roomChatService.publishSystemNotice(
                roomId,
                "HOST_INTRO",
                reasoning,
                buildNoticeExtData("HOST_INTRO", session, nextSpeakerId, reasoning, false)
        );

        return DispatchDecisionVO.builder()
                .speakerId(nextSpeakerId)
                .decisionSource(SOURCE_DEBATE_ARBITRATOR)
                .reasoning(reasoning)
                .build();
    }

    private void ensureRoomExists(String roomId) {
        if (roomId == null || roomId.isBlank() || chatRoomRepository.queryRoomById(roomId) == null) {
            throw new MissingException("房间不存在");
        }
    }

    private void validateClientMember(String roomId, String clientId) {
        Set<String> clientIds = chatRoomRepository.queryClientsByRoomId(roomId).stream()
                .map(AiChatRoomMemberEntity::getMemberId)
                .collect(Collectors.toSet());
        if (!clientIds.contains(clientId)) {
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

    private List<DebateMemberStatusVO> toMemberStatusVO(List<String> clientIds, String roomId) {
        if (clientIds == null) {
            return new ArrayList<>();
        }
        return clientIds.stream()
                .map(clientId -> DebateMemberStatusVO.builder()
                        .clientId(clientId)
                        .clientName(chatRoomRepository.queryMemberName(roomId, clientId))
                        .build())
                .collect(Collectors.toList());
    }

    private String buildHostReasoning(DebateSessionEntity session, String nextSpeakerId) {
        String side = session.getSideForClient(nextSpeakerId);
        String speakerName = chatRoomRepository.queryMemberName(session.getRoomId(), nextSpeakerId);
        String sideName = "PRO".equals(side) ? "正方" : "反方";
        return String.format("第 %d 轮继续，请%s辩手 %s 发言。", session.getCurrentRound(), sideName, speakerName);
    }

    private String buildNoticeExtData(String noticeType, DebateSessionEntity session, String speakerId, String reasoning, boolean waitingForWinner) {
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
        return JSON.toJSONString(data);
    }

    private String buildWinnerExtData(DebateSessionEntity session, String winnerSide) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noticeType", "ROUND_WINNER");
        data.put("sessionId", session.getSessionId());
        data.put("roundNumber", session.getCurrentRound());
        data.put("winnerSide", winnerSide);
        data.put("roundWinners", session.safeRoundWinners());
        return JSON.toJSONString(data);
    }

    private String buildSimpleExtData(String noticeType, String roomId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noticeType", noticeType);
        data.put("roomId", roomId);
        return JSON.toJSONString(data);
    }
}
