package com.dasi.domain.room.service.dispatch.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.room.model.entity.DebateSessionEntity;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.model.valobj.DispatchDecisionVO;
import com.dasi.domain.room.model.valobj.DebateStatus;
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
    private IDebateService debateService;

    @Override
    protected String doApply(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) throws Exception {
        if (dispatchContext.hasAnyDecision()) {
            log.debug("【ARBITRATOR_BYPASS】roomId={}, eventType={}, reason=PRE_DECIDED",
                    strategyEntity.getRoomId(), strategyEntity.getEventType());
            return router(strategyEntity, dispatchContext);
        }

        DebateSessionEntity activeSession = dispatchContext.getDebateSession();
        if (activeSession == null) {
            return router(strategyEntity, dispatchContext);
        }

        if (isUserMessage(strategyEntity)) {
            blockDebatePlainUserMessage(strategyEntity, dispatchContext);
            return router(strategyEntity, dispatchContext);
        }

        if (!canProcessDebateDispatchEvent(strategyEntity, activeSession)) {
            dispatchContext.setTerminateChain(true);
            return router(strategyEntity, dispatchContext);
        }

        DispatchDecisionVO decision = debateService.decideNextDispatch(strategyEntity, dispatchContext);
        if (decision == null) {
            dispatchContext.setTerminateChain(true);
            return router(strategyEntity, dispatchContext);
        }
        dispatchContext.setDecision(decision);
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

    private boolean isUserMessage(DispatchStrategyEntity strategyEntity) {
        return WebSocketEvent.EventType.USER_MSG.equals(strategyEntity.getEventType());
    }

    private boolean canProcessDebateDispatchEvent(DispatchStrategyEntity strategyEntity, DebateSessionEntity activeSession) {
        if (!isDebateDispatchEvent(strategyEntity)) {
            return false;
        }
        return DebateStatus.RUNNING.equals(activeSession.getStatus());
    }

    private boolean isDebateDispatchEvent(DispatchStrategyEntity strategyEntity) {
        String eventType = strategyEntity.getEventType();
        return WebSocketEvent.EventType.CLIENT_MSG_END.equals(eventType)
                || WebSocketEvent.EventType.CLIENT_MSG_ERROR.equals(eventType)
                || WebSocketEvent.EventType.DEBATE_DISPATCH_TRIGGER.equals(eventType);
    }

    private void blockDebatePlainUserMessage(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        dispatchContext.setTerminateChain(true);
        if (dispatchContext.isIntermissionPhase()) {
            log.info("【DEBATE_INTERMISSION_PLAIN_BLOCKED】roomId={}, senderId={}, atMemberIds={}",
                    strategyEntity.getRoomId(), strategyEntity.getSenderId(), strategyEntity.getAtMemberIds());
            return;
        }
        log.info("【DEBATE_RUNNING_PLAIN_BLOCKED】roomId={}, senderId={}",
                strategyEntity.getRoomId(), strategyEntity.getSenderId());
    }
}
