package com.dasi.domain.room.service.dispatch.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.ai.service.dispatch.IDispatchService;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.model.valobj.DispatchDecisionVO;
import com.dasi.domain.room.service.IRoomChatService;
import com.dasi.domain.room.service.dispatch.DispatchContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

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

    @Override
    protected String doApply(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) throws Exception {
        if (!dispatchContext.hasDecision()) {
            log.info("【调度决策】ChatExecutionNode：本轮无命中决策");
            return router(strategyEntity, dispatchContext);
        }

        DispatchDecisionVO decision = dispatchContext.getDecision();
        String roomId = strategyEntity.getRoomId();
        String speakerId = decision.getSpeakerId();

        // 1. 资源检查与动态装配 (确保目标 Client 已加载)
        String beanName = CLIENT.getBeanName(speakerId);
        if (!applicationContext.containsBean(beanName)) {
            log.info("【调度决策】容器中不存在 Bean {}，触发装配策略", beanName);
            aiDispatchService.dispatchArmoryStrategy(ARMORY_CHAT.getType(), Collections.singleton(speakerId));
        }

        // 2. 执行下行指令
        log.info("【调度决策】ChatExecutionNode 执行：roomId={}, speakerId={}, source={}", 
                roomId, speakerId, decision.getDecisionSource());
        roomChatService.clientChat(roomId, speakerId);

        return router(strategyEntity, dispatchContext);
    }

    @Override
    public StrategyHandler<DispatchStrategyEntity, DispatchContext, String> get(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        // 链路彻底结束
        return null;
    }

}
