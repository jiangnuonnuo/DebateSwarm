package com.dasi.domain.room.service.dispatch.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.DebateSessionEntity;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.model.valobj.DispatchDecisionVO;
import com.dasi.domain.room.model.valobj.DebateStatus;
import com.dasi.domain.room.model.valobj.RoomDebateStateVO;
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

    @Resource
    private IChatRoomRepository chatRoomRepository;

    @Override
    protected String doApply(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) throws Exception {
        List<String> atMemberIds = strategyEntity.getAtMemberIds();
        if (atMemberIds == null || atMemberIds.isEmpty()) {
            return router(strategyEntity, dispatchContext);
        }

        DebateSessionEntity activeSession = dispatchContext.getDebateSession();
        if (activeSession == null) {
            activeSession = chatRoomRepository.queryActiveDebateSession(strategyEntity.getRoomId());
            dispatchContext.setDebateSession(activeSession);
        }
        boolean debateIntermission = false;
        if (activeSession != null && DebateStatus.ROUND_END.equals(activeSession.getStatus())) {
            chatRoomRepository.initRoomStateIfAbsent(strategyEntity.getRoomId());
            RoomDebateStateVO roomState = chatRoomRepository.queryRoomDebateState(strategyEntity.getRoomId());
            boolean waitingForWinner = roomState == null || roomState.waitingForWinner();
            if (waitingForWinner) {
                return router(strategyEntity, dispatchContext);
            }
            debateIntermission = true;
        }

        // 按用户 @ 顺序精确校验，确保命中的人确实是当前房间中的 CLIENT。
        List<AiChatRoomMemberEntity> clients = chatRoomRepository.queryClientMembersByIds(strategyEntity.getRoomId(), atMemberIds);
        if (clients == null || clients.isEmpty()) {
            return router(strategyEntity, dispatchContext);
        }

        boolean debateMode = activeSession != null && !debateIntermission;
        List<String> legalDebaters = activeSession == null ? null : activeSession.getAllDebaterIds();
        List<DispatchDecisionVO> collectedDecisions = new ArrayList<>();
        for (AiChatRoomMemberEntity clientMember : clients) {
            String clientId = clientMember.getMemberId();
            if (clientId.equals(strategyEntity.getSenderId())) {
                continue;
            }
            if (debateMode) {
                if (!activeSession.canAcceptMentionOverride(clientId) || !legalDebaters.contains(clientId)) {
                    log.info("【调度决策】AtMentionNode 跳过非合法辩手 mention：clientId={}", clientId);
                    continue;
                }
            }

            DispatchDecisionVO decision = DispatchDecisionVO.builder()
                    .speakerId(clientId)
                    .decisionSource(debateIntermission ? "AT_MENTION_INTERMISSION" : "AT_MENTION")
                    .reasoning(debateMode
                            ? String.format("用户指定由辩手 %s 优先回应。", clientMember.getMemberName())
                            : String.format("用户在同一条消息中指定由 %s 参与回应。", clientMember.getMemberName()))
                    .orderIndex(collectedDecisions.size())
                    .multiAt(!debateMode)
                    .sessionId(activeSession == null ? null : activeSession.getSessionId())
                    .roundNumber(activeSession == null ? null : activeSession.getCurrentRound())
                    .sessionVersion(activeSession == null ? null : activeSession.getVersion())
                    .build();

            if (debateMode) {
                log.info("【调度决策】AtMentionNode 命中辩论指定：clientId={}，roomId={}", clientId, strategyEntity.getRoomId());
                dispatchContext.setDecision(decision);
                break;
            }

            collectedDecisions.add(decision);
        }

        if (!debateMode) {
            if (collectedDecisions.size() == 1) {
                DispatchDecisionVO singleDecision = DispatchDecisionVO.builder()
                        .speakerId(collectedDecisions.get(0).getSpeakerId())
                        .decisionSource(collectedDecisions.get(0).getDecisionSource())
                        .reasoning(collectedDecisions.get(0).getReasoning())
                        .multiAt(false)
                        .build();
                dispatchContext.setDecision(singleDecision);
                if (debateIntermission) {
                    log.info("【DEBATE_INTERMISSION_MENTION_ALLOWED】轮间 @ 放行 roomId={}, clientId={}",
                            strategyEntity.getRoomId(), singleDecision.getSpeakerId());
                } else {
                    log.info("【调度决策】AtMentionNode 命中单目标 @：roomId={}, clientId={}",
                            strategyEntity.getRoomId(), singleDecision.getSpeakerId());
                }
            } else if (!collectedDecisions.isEmpty()) {
                dispatchContext.setDecisions(collectedDecisions);
                dispatchContext.setMultiAtMode(true);
                if (debateIntermission) {
                    log.info("【DEBATE_INTERMISSION_MENTION_ALLOWED】轮间 Multi-At 放行 roomId={}, atMemberIds={}, hitClientIds={}",
                            strategyEntity.getRoomId(),
                            atMemberIds,
                            collectedDecisions.stream().map(DispatchDecisionVO::getSpeakerId).toList());
                } else {
                    log.info("【MULTI_AT_COLLECT】roomId={}, atMemberIds={}, hitClientIds={}",
                            strategyEntity.getRoomId(),
                            atMemberIds,
                            collectedDecisions.stream().map(DispatchDecisionVO::getSpeakerId).toList());
                }
            }
        }

        return router(strategyEntity, dispatchContext);
    }

    @Override
    public StrategyHandler<DispatchStrategyEntity, DispatchContext, String> get(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        // 动态路由：若已产生决策，直跳执行节点；否则继续走仲裁者节点
        if (dispatchContext.hasAnyDecision()) {
            return chatExecutionNode;
        }
        return arbitratorNode;
    }

}
