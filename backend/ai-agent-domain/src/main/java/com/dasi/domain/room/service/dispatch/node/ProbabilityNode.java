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
import java.util.Random;

/**
 * @Author: xerina
 * @Description: 调度规则树 - 概率响应规则节点
 * 职责：在自由模式下，候选人按 50% 概率进行响应。
 */
@Slf4j
@Service
public class ProbabilityNode extends AbstractDispatchNode {

    @Resource
    private ChatExecutionNode chatExecutionNode;

    @Resource
    private IChatRoomRepository chatRoomRepository;

    private final Random random = new Random();

    /** 响应概率 (50%) */
    private static final double RESPONSE_PROBABILITY = 0.5;

    @Override
    protected String doApply(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) throws Exception {
        // 如果已经有决策，直接跳过
        if (dispatchContext.hasDecision()) {
            return router(strategyEntity, dispatchContext);
        }

        if (random.nextDouble() >= RESPONSE_PROBABILITY) {
            return router(strategyEntity, dispatchContext);
        }

        List<AiChatRoomMemberEntity> clients = chatRoomRepository.queryClientsByRoomId(strategyEntity.getRoomId());
        if (clients == null || clients.isEmpty()) return router(strategyEntity, dispatchContext);

        List<AiChatRoomMemberEntity> candidates = clients.stream()
                .filter(clientMember -> !clientMember.getMemberId().equals(strategyEntity.getSenderId()))
                .toList();
        if (candidates.isEmpty()) {
            return router(strategyEntity, dispatchContext);
        }

        AiChatRoomMemberEntity selected = candidates.get(random.nextInt(candidates.size()));
        log.info("【调度决策】ProbabilityNode 命中：clientId={}", selected.getMemberId());
        dispatchContext.setDecision(DispatchDecisionVO.builder()
                .speakerId(selected.getMemberId())
                .decisionSource("PROBABILITY")
                .reasoning(String.format("自由聊天命中，由 %s 随机接话。", selected.getMemberName()))
                .build());
        return router(strategyEntity, dispatchContext);
    }

    @Override
    public StrategyHandler<DispatchStrategyEntity, DispatchContext, String> get(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        return chatExecutionNode;
    }

}
