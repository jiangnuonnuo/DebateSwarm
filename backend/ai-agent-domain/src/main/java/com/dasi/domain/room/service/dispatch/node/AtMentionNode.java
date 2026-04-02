package com.dasi.domain.room.service.dispatch.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.model.valobj.DispatchDecisionVO;
import com.dasi.domain.room.service.dispatch.DispatchContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

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
        Set<String> atMemberIds = strategyEntity.getAtMemberIds();
        if (atMemberIds == null || atMemberIds.isEmpty()) {
            return router(strategyEntity, dispatchContext);
        }

        // 获取房间内候选 Client (确保被 @ 的人确实是 CLIENT 且在房间内)
        List<AiChatRoomMemberEntity> clients = chatRoomRepository.queryClientsByRoomId(strategyEntity.getRoomId());
        if (clients == null || clients.isEmpty()) {
            return router(strategyEntity, dispatchContext);
        }

        for (AiChatRoomMemberEntity clientMember : clients) {
            String clientId = clientMember.getMemberId();
            if (clientId.equals(strategyEntity.getSenderId())) continue;

            if (atMemberIds.contains(clientId)) {
                log.info("【调度决策】AtMentionNode 命中：clientId={}", clientId);
                dispatchContext.setDecision(DispatchDecisionVO.builder()
                        .speakerId(clientId)
                        .decisionSource("AT_MENTION")
                        .build());
                // 已产生决策，准备路由
                break;
            }
        }

        return router(strategyEntity, dispatchContext);
    }

    @Override
    public StrategyHandler<DispatchStrategyEntity, DispatchContext, String> get(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        // 动态路由：若已产生决策，直跳执行节点；否则继续走仲裁者节点
        if (dispatchContext.hasDecision()) {
            return chatExecutionNode;
        }
        return arbitratorNode;
    }

}
