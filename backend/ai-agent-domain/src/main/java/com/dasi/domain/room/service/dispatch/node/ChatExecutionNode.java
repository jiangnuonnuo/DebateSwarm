package com.dasi.domain.room.service.dispatch.node;

import com.alibaba.fastjson2.JSON;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.ai.service.dispatch.IDispatchService;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.DebateSessionEntity;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.model.valobj.ClientExecutionRequestVO;
import com.dasi.domain.room.model.valobj.DispatchDecisionVO;
import com.dasi.domain.room.model.valobj.RoomDebateStateVO;
import com.dasi.domain.room.service.IRoomChatService;
import com.dasi.domain.room.service.dispatch.DispatchContext;
import com.dasi.domain.room.service.chat.IMultiMentionExecutionService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.dasi.domain.ai.model.enumeration.AiArmoryType.ARMORY_CHAT;
import static com.dasi.domain.ai.model.enumeration.AiType.CLIENT;

/**
 * @Author: xerina
 * @Description: 调度规则树 - 聊天执行节点
 * 职责：作为决策树的汇聚终点，负责资源检查、装配并下达最终的 clientChat 执行指令。
 */
@Slf4j
@Service
public class ChatExecutionNode extends AbstractDispatchNode {

    @Resource
    private IRoomChatService roomChatService;

    @Resource
    private IDispatchService aiDispatchService;

    @Resource
    private IChatRoomRepository chatRoomRepository;

    @Resource
    private IMultiMentionExecutionService multiMentionExecutionService;

    @Override
    protected String doApply(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) throws Exception {
        if (!dispatchContext.hasAnyDecision()) {
            log.info("【调度决策】ChatExecutionNode：本轮无命中决策");
            return router(strategyEntity, dispatchContext);
        }

        if (dispatchContext.isMultiAtMode() && dispatchContext.hasDecisions()) {
            executeMultiAt(strategyEntity, dispatchContext);
            return router(strategyEntity, dispatchContext);
        }

        DispatchDecisionVO decision = dispatchContext.getDecision();
        String roomId = strategyEntity.getRoomId();
        String speakerId = decision.getSpeakerId();

        DebateSessionEntity activeSession = null;
        if (decision.getSessionId() != null && !decision.getSessionId().isBlank()) {
            activeSession = chatRoomRepository.queryActiveDebateSession(roomId);
            if (activeSession == null
                    || !Objects.equals(activeSession.getSessionId(), decision.getSessionId())
                    || !Objects.equals(activeSession.getCurrentRound(), decision.getRoundNumber())
                    || !Objects.equals(activeSession.getVersion(), decision.getSessionVersion())) {
                log.info("【STALE_CALLBACK_DROPPED】执行前丢弃过期调度 roomId={}, speakerId={}, sessionId={}, currentSessionId={}, traceId={}",
                        roomId,
                        speakerId,
                        decision.getSessionId(),
                        activeSession == null ? null : activeSession.getSessionId(),
                        decision.getDispatchTraceId());
                return router(strategyEntity, dispatchContext);
            }

            decision = ensureDebateTrace(decision, activeSession);
            if (!preparePendingDispatch(roomId, activeSession, decision)) {
                log.info("【STALE_CALLBACK_DROPPED】调度护栏已变化，放弃执行 roomId={}, speakerId={}, traceId={}",
                        roomId, speakerId, decision.getDispatchTraceId());
                return router(strategyEntity, dispatchContext);
            }

            if (decision.getReasoning() != null && !decision.getReasoning().isBlank()) {
                roomChatService.publishSystemNotice(
                        roomId,
                        "HOST_INTRO",
                        decision.getReasoning(),
                        buildHostIntroExtData(activeSession, decision)
                );
            }
        }

        // 1. 资源检查与动态装配 (确保目标 Client 已加载)
        String beanName = CLIENT.getBeanName(speakerId);
        if (!applicationContext.containsBean(beanName)) {
            log.info("【CHAT_EXECUTE】容器中不存在 Bean {}，触发装配策略", beanName);
            aiDispatchService.dispatchArmoryStrategy(ARMORY_CHAT.getType(), Collections.singleton(speakerId));
        }
        if (!applicationContext.containsBean(beanName)) {
            log.warn("【CHAT_EXECUTE】装配后仍未找到 Bean roomId={}, speakerId={}, beanName={}", roomId, speakerId, beanName);
        }

        // 2. 构造执行请求
        ClientExecutionRequestVO executionRequest = ClientExecutionRequestVO.builder()
                .roomId(roomId)
                .clientId(speakerId)
                .sessionId(activeSession == null ? decision.getSessionId() : activeSession.getSessionId())
                .roundNumber(activeSession == null ? decision.getRoundNumber() : activeSession.getCurrentRound())
                .sessionVersion(activeSession == null ? decision.getSessionVersion() : activeSession.getVersion())
                .dispatchTraceId(decision.getDispatchTraceId())
                .decisionSource(decision.getDecisionSource())
                .reasoning(decision.getReasoning())
                .build();

        // 3. 执行下行指令
        log.info("【CHAT_EXECUTE】执行辩手发言 roomId={}, speakerId={}, traceId={}, source={}",
                roomId, speakerId, decision.getDispatchTraceId(), decision.getDecisionSource());
        roomChatService.clientChat(executionRequest);

        return router(strategyEntity, dispatchContext);
    }

    @Override
    public StrategyHandler<DispatchStrategyEntity, DispatchContext, String> get(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        // 链路彻底结束
        return null;
    }

    private boolean preparePendingDispatch(String roomId, DebateSessionEntity activeSession, DispatchDecisionVO decision) {
        chatRoomRepository.initRoomStateIfAbsent(roomId);
        RoomDebateStateVO state = chatRoomRepository.queryRoomDebateState(roomId);
        if (state == null) {
            state = RoomDebateStateVO.builder().version(0).build();
        }
        if (!Objects.equals(state.getActiveDebateSessionId(), activeSession.getSessionId())) {
            return false;
        }

        state.setPendingSpeakerId(decision.getSpeakerId());
        state.setPendingTurnNumber((activeSession.getCurrentTurn() == null ? 0 : activeSession.getCurrentTurn()) + 1);
        state.setPendingDecisionSource(decision.getDecisionSource());
        state.setPendingDecisionReasoning(decision.getReasoning());
        state.setDispatchSessionId(activeSession.getSessionId());
        state.setDispatchRound(activeSession.getCurrentRound());
        state.setDispatchVersion(activeSession.getVersion());
        state.setDispatchTraceId(decision.getDispatchTraceId());
        if (decision.getRequiredSide() != null && !decision.getRequiredSide().isBlank()) {
            state.setSlotRequiredSide(decision.getRequiredSide());
        }

        Integer version = state.getVersion() == null ? 0 : state.getVersion();
        boolean saved = chatRoomRepository.saveRoomDebateState(roomId, state, version);
        if (saved) {
            state.setVersion(version + 1);
        }
        return saved;
    }

    private String buildHostIntroExtData(DebateSessionEntity session, DispatchDecisionVO decision) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("noticeType", "HOST_INTRO");
        data.put("sessionId", session.getSessionId());
        data.put("topic", session.getTopic());
        data.put("roundNumber", session.getCurrentRound());
        data.put("currentTurn", session.getCurrentTurn());
        data.put("turnsPerRound", session.getTurnsPerRound());
        data.put("speakerId", decision.getSpeakerId());
        data.put("reasoning", decision.getReasoning());
        data.put("decisionSource", decision.getDecisionSource());
        data.put("dispatchTraceId", decision.getDispatchTraceId());
        return JSON.toJSONString(data);
    }

    private DispatchDecisionVO ensureDebateTrace(DispatchDecisionVO decision, DebateSessionEntity activeSession) {
        if (decision.getDispatchTraceId() != null && !decision.getDispatchTraceId().isBlank()) {
            return decision;
        }
        return DispatchDecisionVO.builder()
                .speakerId(decision.getSpeakerId())
                .decisionSource(decision.getDecisionSource())
                .reasoning(decision.getReasoning())
                .sessionId(decision.getSessionId())
                .roundNumber(decision.getRoundNumber())
                .sessionVersion(decision.getSessionVersion() == null ? activeSession.getVersion() : decision.getSessionVersion())
                .dispatchTraceId("trace_" + activeSession.getSessionId() + "_" + activeSession.getCurrentRound() + "_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                .requiredSide(decision.getRequiredSide())
                .build();
    }

    /**
     * Multi-At 只允许在自由聊天模式下批量执行，因此不复用辩论的 pending speaker 护栏。
     * 这里将多目标请求拆成独立的 client 生成任务，交给批量执行服务并发处理。
     */
    private void executeMultiAt(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        List<DispatchDecisionVO> decisions = dispatchContext.getDecisions();
        if (decisions == null || decisions.isEmpty()) {
            return;
        }

        String roomId = strategyEntity.getRoomId();
        String batchTraceId = "multiat_" + roomId + "_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        List<String> requestedAtMemberIds = strategyEntity.getAtMemberIds() == null
                ? List.of()
                : new ArrayList<>(strategyEntity.getAtMemberIds());

        Set<String> speakerIds = new LinkedHashSet<>();
        for (DispatchDecisionVO decision : decisions) {
            if (decision.getSpeakerId() != null && !decision.getSpeakerId().isBlank()) {
                speakerIds.add(decision.getSpeakerId());
            }
        }
        if (!speakerIds.isEmpty()) {
            aiDispatchService.dispatchArmoryStrategy(ARMORY_CHAT.getType(), speakerIds);
        }

        List<ClientExecutionRequestVO> requests = new ArrayList<>();
        for (int i = 0; i < decisions.size(); i++) {
            DispatchDecisionVO decision = decisions.get(i);
            requests.add(ClientExecutionRequestVO.builder()
                    .roomId(roomId)
                    .clientId(decision.getSpeakerId())
                    .decisionSource(decision.getDecisionSource())
                    .reasoning(decision.getReasoning())
                    .batchTraceId(batchTraceId)
                    .orderIndex(decision.getOrderIndex() == null ? i : decision.getOrderIndex())
                    .requestedAtMemberIds(requestedAtMemberIds)
                    .build());
        }

        log.info("【MULTI_AT_EXECUTE】批量触发自由聊天 @ 响应 roomId={}, batchTraceId={}, speakerIds={}",
                roomId, batchTraceId, speakerIds);
        multiMentionExecutionService.executeBatch(requests);
    }

}
