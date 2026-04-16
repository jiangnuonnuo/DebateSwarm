package com.dasi.domain.room.service.dispatch.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.DebateSessionEntity;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.model.valobj.DebateStatus;
import com.dasi.domain.room.model.valobj.RoomDebateStateVO;
import com.dasi.domain.room.service.dispatch.DispatchContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * @Author: xerina
 * @Description: 调度规则树 - 根节点
 * 职责：作为调度决策树的入口，统一装配通用上下文后分发至首个规则节点。
 */
@Slf4j
@Service
public class DispatchRootNode extends AbstractDispatchNode {

    @Resource
    private AtMentionNode atMentionNode;

    @Resource
    private IChatRoomRepository chatRoomRepository;

    @Override
    protected String doApply(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) throws Exception {
        assembleCommonContext(strategyEntity, dispatchContext);
        log.info("【DISPATCH_ROOT_READY】roomId={}, eventType={}, phase={}, mentionCount={}",
                strategyEntity.getRoomId(),
                strategyEntity.getEventType(),
                dispatchContext.getDebatePhase(),
                dispatchContext.getMentionCandidates() == null ? 0 : dispatchContext.getMentionCandidates().size());
        return router(strategyEntity, dispatchContext);
    }

    @Override
    public StrategyHandler<DispatchStrategyEntity, DispatchContext, String> get(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        // 第一跳进入 @指定规则
        return atMentionNode;
    }

    private void assembleCommonContext(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        loadDebateSnapshot(strategyEntity.getRoomId(), dispatchContext);
        loadMentionCandidates(strategyEntity, dispatchContext);
    }

    private void loadDebateSnapshot(String roomId, DispatchContext dispatchContext) {
        DebateSessionEntity activeSession = chatRoomRepository.queryActiveDebateSession(roomId);
        RoomDebateStateVO roomState = null;
        if (activeSession != null) {
            chatRoomRepository.initRoomStateIfAbsent(roomId);
            roomState = chatRoomRepository.queryRoomDebateState(roomId);
        }
        dispatchContext.setDebateSession(activeSession);
        dispatchContext.setRoomDebateState(roomState);
        dispatchContext.setDebatePhase(resolveDebatePhase(activeSession, roomState));
    }

    private void loadMentionCandidates(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        List<String> atMemberIds = strategyEntity.getAtMemberIds();
        if (atMemberIds == null || atMemberIds.isEmpty()) {
            dispatchContext.setMentionCandidates(new ArrayList<>());
            return;
        }

        List<AiChatRoomMemberEntity> members = chatRoomRepository.queryClientMembersByIds(strategyEntity.getRoomId(), atMemberIds);
        if (members == null || members.isEmpty()) {
            dispatchContext.setMentionCandidates(new ArrayList<>());
            return;
        }

        Map<String, AiChatRoomMemberEntity> memberMap = new LinkedHashMap<>();
        for (AiChatRoomMemberEntity member : members) {
            if (member != null && member.getMemberId() != null && !member.getMemberId().isBlank()) {
                memberMap.put(member.getMemberId(), member);
            }
        }

        List<AiChatRoomMemberEntity> orderedCandidates = new ArrayList<>();
        for (String memberId : new LinkedHashSet<>(atMemberIds)) {
            AiChatRoomMemberEntity candidate = memberMap.get(memberId);
            if (candidate == null) {
                continue;
            }
            if (memberId.equals(strategyEntity.getSenderId())) {
                continue;
            }
            orderedCandidates.add(candidate);
        }
        dispatchContext.setMentionCandidates(orderedCandidates);
    }

    private String resolveDebatePhase(DebateSessionEntity activeSession, RoomDebateStateVO roomState) {
        if (activeSession == null || activeSession.getStatus() == null) {
            return DispatchContext.PHASE_FREE_CHAT;
        }
        if (DebateStatus.RUNNING.equals(activeSession.getStatus())) {
            return DispatchContext.PHASE_RUNNING;
        }
        if (DebateStatus.ROUND_END.equals(activeSession.getStatus())) {
            boolean waitingForWinner = roomState == null || roomState.waitingForWinner();
            return waitingForWinner
                    ? DispatchContext.PHASE_ROUND_END_WAIT_WINNER
                    : DispatchContext.PHASE_INTERMISSION;
        }
        if (DebateStatus.FINISHED.equals(activeSession.getStatus())) {
            return DispatchContext.PHASE_FINISHED;
        }
        return DispatchContext.PHASE_FREE_CHAT;
    }
}
