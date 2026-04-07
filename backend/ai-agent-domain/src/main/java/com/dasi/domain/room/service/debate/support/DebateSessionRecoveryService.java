package com.dasi.domain.room.service.debate.support;

import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.DebateSessionEntity;
import com.dasi.domain.room.model.valobj.RoomDebateStateVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 辩论会话恢复服务。
 * 负责收敛“room_state 与 session 不一致”的异常状态，避免前端被僵尸会话锁死。
 */
@Slf4j
@Service
public class DebateSessionRecoveryService {

    @Resource
    private IChatRoomRepository chatRoomRepository;

    public RoomDebateStateVO sanitizeRoomState(String roomId, RoomDebateStateVO state, boolean healDetachedOrphanSession) {
        RoomDebateStateVO normalizedState = ensureStateVersion(state);
        boolean stateDirty = false;

        if (hasText(normalizedState.getActiveDebateSessionId())) {
            DebateSessionEntity boundSession = chatRoomRepository.queryDebateSessionBySessionId(normalizedState.getActiveDebateSessionId());
            if (boundSession == null || boundSession.isFinished()) {
                log.warn("【DEBATE_STATE_SANITIZE】发现失效 activeSession 引用，执行状态清理 roomId={}, activeSessionId={}",
                        roomId, normalizedState.getActiveDebateSessionId());
                clearActiveDebateState(normalizedState);
                stateDirty = true;
            }
        }

        if (!hasText(normalizedState.getActiveDebateSessionId()) && healDetachedOrphanSession) {
            DebateSessionEntity orphanSession = chatRoomRepository.queryActiveDebateSession(roomId);
            if (orphanSession != null) {
                orphanSession.finishDebate();
                boolean updated = chatRoomRepository.forceUpdateDebateSessionStatus(orphanSession);
                if (updated) {
                    log.info("【DEBATE_ORPHAN_HEALED】已收敛脱锚活跃会话 roomId={}, sessionId={}",
                            roomId, orphanSession.getSessionId());
                } else {
                    log.warn("【DEBATE_ORPHAN_HEAL_FAILED】脱锚活跃会话收敛失败 roomId={}, sessionId={}",
                            roomId, orphanSession.getSessionId());
                }
            }
        }

        if (stateDirty) {
            boolean saved = chatRoomRepository.forceSaveRoomDebateState(roomId, normalizedState);
            if (!saved) {
                log.warn("【DEBATE_STATE_SANITIZE】房间运行态强制清理失败 roomId={}", roomId);
            }
            RoomDebateStateVO fresh = chatRoomRepository.queryRoomDebateStateFresh(roomId);
            return ensureStateVersion(fresh == null ? normalizedState : fresh);
        }
        return normalizedState;
    }

    private void clearActiveDebateState(RoomDebateStateVO state) {
        state.setActiveDebateSessionId(null);
        state.setPendingRoundNumber(null);
        state.setPendingRoundTurnCount(null);
        state.setLastRoundEndedAt(null);
        state.setLastRoundSummary(null);

        state.setPendingSpeakerId(null);
        state.setPendingTurnNumber(null);
        state.setPendingDecisionSource(null);
        state.setPendingDecisionReasoning(null);
        state.setDispatchSessionId(null);
        state.setDispatchRound(null);
        state.setDispatchVersion(null);
        state.setDispatchTraceId(null);

        state.setSlotRequiredSide(null);
        state.setSlotAttemptedSpeakerIds(java.util.List.of());
        state.setSlotRetryCount(0);
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

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
