package com.dasi.domain.room.service.dispatch.node;

import com.alibaba.fastjson2.JSON;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.ai.service.dispatch.IDispatchService;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.DebateSessionEntity;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.model.valobj.DispatchDecisionVO;
import com.dasi.domain.room.model.valobj.RoomDebateStateVO;
import com.dasi.domain.room.service.IRoomChatService;
import com.dasi.domain.room.service.dispatch.DispatchContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Objects;

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

    @Override
    protected String doApply(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) throws Exception {
        if (!dispatchContext.hasDecision()) {
            log.info("【调度决策】ChatExecutionNode：本轮无命中决策");
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
                log.info("【CHAT_EXECUTE】丢弃过期调度 roomId={}, speakerId={}, sessionId={}, currentSessionId={}",
                        roomId,
                        speakerId,
                        decision.getSessionId(),
                        activeSession == null ? null : activeSession.getSessionId());
                return router(strategyEntity, dispatchContext);
            }

            if (!preparePendingDispatch(roomId, activeSession, decision)) {
                log.info("【CHAT_EXECUTE】调度护栏已变化，放弃执行 roomId={}, speakerId={}", roomId, speakerId);
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

        // 2. 执行下行指令
        log.info("【CHAT_EXECUTE】执行辩手发言 roomId={}, speakerId={}, source={}",
                roomId, speakerId, decision.getDecisionSource());
        roomChatService.clientChat(roomId, speakerId);

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
        return JSON.toJSONString(data);
    }

}
