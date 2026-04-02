package com.dasi.domain.room.service.dispatch.node;

import cn.bugstack.wrench.design.framework.tree.AbstractMultiThreadStrategyRouter;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.service.dispatch.DispatchContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

/**
 * @Author: xerina
 * @Description: 调度规则节点抽象基类
 * 职责：对接框架模板，提供通用的 Bean 获取和日志能力。
 */
@Slf4j
public abstract class AbstractDispatchNode extends AbstractMultiThreadStrategyRouter<DispatchStrategyEntity, DispatchContext, String> {

    @Resource
    protected ApplicationContext applicationContext;

    @Override
    protected void multiThread(DispatchStrategyEntity strategyEntity, DispatchContext dispatchContext) {
        // 调度决策目前为顺序逻辑，暂不启用并发分支
    }

    /**
     * 获取 Spring Bean 助手
     */
    protected <T> T getBean(Class<T> requiredType) {
        return applicationContext.getBean(requiredType);
    }

}
