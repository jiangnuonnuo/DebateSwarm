package com.dasi.domain.room.service.dispatch;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.service.dispatch.node.DispatchRootNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author: xerina
 * @Description: 调度策略工厂
 * 职责：封装调度决策树的入口，管理调度上下文。
 */
@Slf4j
@Service
public class DispatchStrategyFactory {

    @Resource
    private DispatchRootNode dispatchRootNode;

    /**
     * 获取调度根节点
     */
    public StrategyHandler<DispatchStrategyEntity, DispatchContext, String> getDispatchRootNode() {
        return dispatchRootNode;
    }

    /**
     * 执行调度决策
     * @param strategyEntity 调度输入实体
     */
    public void doDispatch(DispatchStrategyEntity strategyEntity) {
        try {
            // 1. 初始化上下文 (内部管理)
            DispatchContext context = new DispatchContext();

            // 2. 启动路由链路
            dispatchRootNode.router(strategyEntity, context);

        } catch (Exception e) {
            log.error("【调度决策】执行异常：roomId={}, eventType={}", 
                    strategyEntity.getRoomId(), strategyEntity.getEventType(), e);
        }
    }

}
