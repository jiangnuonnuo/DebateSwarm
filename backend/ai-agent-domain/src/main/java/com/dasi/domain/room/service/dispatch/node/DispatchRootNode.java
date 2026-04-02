package com.dasi.domain.room.service.dispatch.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.service.dispatch.DispatchContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author: xerina
 * @Description: 调度规则树 - 根节点
 * 职责：作为调度决策树的入口，分发至首个规则节点。
 */
@Slf4j
@Service
public class DispatchRootNode extends AbstractDispatchNode {

    @Resource
    private AtMentionNode atMentionNode;

    @Override
    protected String doApply(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) throws Exception {
        log.info("【调度决策】RootNode 入口：roomId={}, eventType={}", strategyEntity.getRoomId(), strategyEntity.getEventType());
        // 启动路由
        return router(strategyEntity, dispatchContext);
    }

    @Override
    public StrategyHandler<DispatchStrategyEntity, DispatchContext, String> get(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        // 第一跳进入 @指定规则
        return atMentionNode;
    }

}
