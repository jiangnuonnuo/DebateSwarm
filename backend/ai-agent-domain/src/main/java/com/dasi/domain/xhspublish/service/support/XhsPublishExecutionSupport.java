package com.dasi.domain.xhspublish.service.support;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAccountBindingEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishContentAssetEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.strategy.IXhsPublishExecuteStrategy;
import com.dasi.domain.xhspublish.service.strategy.XhsPublishExecuteStrategyFactory;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
public class XhsPublishExecutionSupport {

    @Resource
    private XhsPublishExecuteStrategyFactory strategyFactory;

    public void execute(XhsPublishTaskEntity task,
                        XhsPublishAttemptEntity attempt,
                        XhsPublishAccountBindingEntity binding,
                        List<XhsPublishContentAssetEntity> assetList) {
        // 执行支持类只做 3 件事：
        // 1. 根据 publishMode + publishType 选择执行策略
        // 2. 解析 latestContextJson/requestJson 为 sourceContext
        // 3. 把 task/attempt/binding/asset/sourceContext 组装成执行树上下文
        IXhsPublishExecuteStrategy strategy = strategyFactory.getStrategy(task.getPublishMode(), task.getPublishType());
        if (strategy == null) {
            throw new WorkException("当前发布模式/类型尚未接入执行策略");
        }

        String sourceJson = StringUtils.hasText(task.getLatestContextJson()) ? task.getLatestContextJson() : task.getRequestJson();
        JSONObject sourceContext = JSON.parseObject(sourceJson);
        strategy.execute(XhsPublishExecutionContext.builder()
                .task(task)
                .attempt(attempt)
                .binding(binding)
                .assetList(assetList)
                .sourceContext(sourceContext)
                .build());
    }

}
