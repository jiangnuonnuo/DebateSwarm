package com.dasi.domain.room.service.dispatch.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.DebateSessionEntity;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.model.valobj.DispatchDecisionVO;
import com.dasi.domain.room.service.dispatch.DispatchContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: xerina
 * @Description: 调度规则树 - @指定回复规则节点
 * 职责：检查是否有被 @ 的候选人，若命中则强制回复。
 */
@Slf4j
@Service
public class AtMentionNode extends AbstractDispatchNode {

    @Resource
    private ArbitratorNode arbitratorNode;

    @Resource
    private ChatExecutionNode chatExecutionNode;

    @Override
    protected String doApply(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) throws Exception {
        if (!dispatchContext.hasMentionCandidates()) {
            return router(strategyEntity, dispatchContext);
        }

        List<DispatchDecisionVO> decisions = buildMentionDecisions(dispatchContext);
        if (decisions.isEmpty()) {
            return router(strategyEntity, dispatchContext);
        }

        applyMentionDecisions(strategyEntity, dispatchContext, decisions);
        return router(strategyEntity, dispatchContext);
    }

    private List<DispatchDecisionVO> buildMentionDecisions(DispatchContext dispatchContext) {
        List<DispatchDecisionVO> decisions = new ArrayList<>();
        DebateSessionEntity session = dispatchContext.getDebateSession();
        boolean multiAt = dispatchContext.getMentionCandidates().size() > 1;
        for (int i = 0; i < dispatchContext.getMentionCandidates().size(); i++) {
            AiChatRoomMemberEntity member = dispatchContext.getMentionCandidates().get(i);
            decisions.add(buildDecision(session, dispatchContext.getDebatePhase(), member, i, multiAt));
        }
        return decisions;
    }

    private DispatchDecisionVO buildDecision(DebateSessionEntity activeSession,
                                             String phase,
                                             AiChatRoomMemberEntity clientMember,
                                             int orderIndex,
                                             boolean multiAt) {
        boolean debateExists = activeSession != null;
        String decisionSource = DispatchContext.PHASE_INTERMISSION.equals(phase)
                ? "AT_MENTION_INTERMISSION"
                : "AT_MENTION";
        return DispatchDecisionVO.builder()
                .speakerId(clientMember.getMemberId())
                .decisionSource(decisionSource)
                .reasoning(debateExists
                        ? String.format("用户在辩论阶段 @ 指定由 %s 回复。", clientMember.getMemberName())
                        : String.format("用户在聊天中 @ 指定由 %s 回复。", clientMember.getMemberName()))
                .orderIndex(orderIndex)
                .multiAt(multiAt)
                // @ 指定统一走聊天解耦链，不参与辩论 turn/slot 推进。
                .sessionId(null)
                .roundNumber(null)
                .sessionVersion(null)
                .build();
    }

    private void applyMentionDecisions(DispatchStrategyEntity strategyEntity,
                                       DispatchContext dispatchContext,
                                       List<DispatchDecisionVO> decisions) {
        if (decisions.size() == 1) {
            dispatchContext.setDecision(decisions.get(0));
            dispatchContext.setMultiAtMode(false);
            log.info("【AT_MENTION_HIT】roomId={}, phase={}, clientId={}",
                    strategyEntity.getRoomId(),
                    dispatchContext.getDebatePhase(),
                    decisions.get(0).getSpeakerId());
            return;
        }

        dispatchContext.setDecisions(decisions);
        dispatchContext.setMultiAtMode(true);
        log.info("【MULTI_AT_COLLECT】roomId={}, phase={}, atMemberIds={}, hitClientIds={}",
                strategyEntity.getRoomId(),
                dispatchContext.getDebatePhase(),
                strategyEntity.getAtMemberIds(),
                decisions.stream().map(DispatchDecisionVO::getSpeakerId).toList());
    }

    @Override
    public StrategyHandler<DispatchStrategyEntity, DispatchContext, String> get(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        // 动态路由：若已产生决策，直跳执行节点；否则继续走仲裁者节点
        return dispatchContext.hasAnyDecision() ? chatExecutionNode : arbitratorNode;
    }

}
