package com.dasi.domain.room.service.dispatch.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.DebateSessionEntity;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.model.valobj.DispatchDecisionVO;
import com.dasi.domain.room.model.valobj.DebateStatus;
import com.dasi.domain.room.model.valobj.RoomDebateStateVO;
import com.dasi.domain.room.model.valobj.WebSocketEvent;
import com.dasi.domain.room.service.dispatch.DispatchContext;
import com.dasi.domain.room.service.debate.IDebateService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author: xerina
 * @Description: 调度规则树 - 仲裁者/辩论模式规则节点
 * 职责：判断是否开启辩论模式。若是，则记录本轮发言并触发仲裁调度。
 */
@Slf4j
@Service
public class ArbitratorNode extends AbstractDispatchNode {

    @Resource
    private ProbabilityNode probabilityNode;

    @Resource
    private ChatExecutionNode chatExecutionNode;

    @Resource
    private IChatRoomRepository chatRoomRepository;

    @Resource
    private IDebateService debateService;

    @Override
    protected String doApply(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) throws Exception {
        // 如果已经有决策 (如来自 @指定)，直接路由
        if (dispatchContext.hasAnyDecision()) {
            if (dispatchContext.getDecision() != null) {
                log.info("【ARBITRATOR_BYPASS】roomId={}, eventType={}, reason=PRE_DECIDED, source={}, speakerId={}",
                        strategyEntity.getRoomId(),
                        strategyEntity.getEventType(),
                        dispatchContext.getDecision().getDecisionSource(),
                        dispatchContext.getDecision().getSpeakerId());
            } else {
                log.info("【ARBITRATOR_BYPASS】roomId={}, eventType={}, reason=PRE_DECIDED_BATCH, size={}",
                        strategyEntity.getRoomId(),
                        strategyEntity.getEventType(),
                        dispatchContext.getDecisions() == null ? 0 : dispatchContext.getDecisions().size());
            }
            return router(strategyEntity, dispatchContext);
        }

        // 1. 查询活跃辩论会话 (利用 IChatRoomRepository 的缓存)
        DebateSessionEntity activeSession = chatRoomRepository.queryActiveDebateSession(strategyEntity.getRoomId());
        if (activeSession == null) {
            // 非辩论模式，继续路由
            return router(strategyEntity, dispatchContext);
        }

        // 2. 存入上下文供后续阶段使用
        dispatchContext.setDebateSession(activeSession);
        log.info("【调度决策】ArbitratorNode 识别到辩论模式：sessionId={}", activeSession.getSessionId());

        boolean isUserMsg = WebSocketEvent.EventType.USER_MSG.equals(strategyEntity.getEventType());
        if (DebateStatus.ROUND_END.equals(activeSession.getStatus())) {
            chatRoomRepository.initRoomStateIfAbsent(strategyEntity.getRoomId());
            RoomDebateStateVO roomState = chatRoomRepository.queryRoomDebateState(strategyEntity.getRoomId());
            boolean waitingForWinner = roomState == null || roomState.waitingForWinner();

            // 轮间窗口（已宣判、待下一轮）：允许 @ 命中；未@普通消息直接阻断，不进入辩论推进逻辑。
            if (isUserMsg && !waitingForWinner) {
                log.info("【DEBATE_INTERMISSION_PLAIN_BLOCKED】轮间普通消息不触发自动回复 roomId={}, senderId={}, atMemberIds={}",
                        strategyEntity.getRoomId(), strategyEntity.getSenderId(), strategyEntity.getAtMemberIds());
                dispatchContext.setTerminateChain(true);
                return router(strategyEntity, dispatchContext);
            }

            // ROUND_END 阶段不处理辩论推进事件，避免误把轮间聊天回流写入辩论状态机。
            dispatchContext.setTerminateChain(true);
            return router(strategyEntity, dispatchContext);
        }

        DispatchDecisionVO decision = debateService.decideNextDispatch(strategyEntity, dispatchContext);
        if (decision != null) {
            dispatchContext.setDecision(decision);
        } else {
            dispatchContext.setTerminateChain(true);
        }

        return router(strategyEntity, dispatchContext);
    }

    @Override
    public StrategyHandler<DispatchStrategyEntity, DispatchContext, String> get(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        // 动态路由：若已产生决策（仲裁成功），直跳执行节点；否则流向概率回复节点
        if (dispatchContext.hasAnyDecision()) {
            return chatExecutionNode;
        }
        if (dispatchContext.isTerminateChain()) {
            return null;
        }
        return probabilityNode;
    }

}
