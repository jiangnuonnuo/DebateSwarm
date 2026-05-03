package com.dasi.domain.xhspublish.service.bmode.publish.node;

import com.alibaba.fastjson2.JSON;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAccountBindingEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptStatsEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.model.valobj.PublishAttemptStatusVO;
import com.dasi.domain.xhspublish.model.valobj.PublishStageVO;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.domain.IPublishTaskStateMachine;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Slf4j
@Service("bPublishAttemptCreateNode")
public class BPublishAttemptCreateNode extends AbstractBPublishExecuteNode {

    @Resource
    private IXhsPublishRepository publishRepository;

    @Resource
    private IPublishTaskStateMachine taskStateMachine;

    @Resource
    private XhsPublishIdSupport idSupport;

    @Resource
    private BPublishPayloadAssembleNode payloadAssembleNode;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        // 第 4 步：节点自己创建 attempt，并把本轮执行真正需要的数据灌入树上下文。
        XhsPublishTaskEntity task = dynamicContext.getTask();
        XhsPublishAccountBindingEntity binding = dynamicContext.getBinding();

        if (StringUtils.hasText(dynamicContext.getOverrideContextJson())) {
            task.setLatestContextJson(dynamicContext.getOverrideContextJson());
        }
        task.setBindingId(binding.getBindingId());
        if (dynamicContext.isRetryMode()) {
            task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "retry_requested"));
            task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "start_execute"));
        } else {
            task.setTaskStatus(taskStateMachine.nextTaskStatus(task.getTaskStatus(), "submit_requested"));
            task.setCurrentStage(taskStateMachine.nextStage(task.getCurrentStage(), "submit_requested"));
        }
        publishRepository.saveTask(task);

        int attemptNo = nextAttemptNo(task.getTaskId(), dynamicContext.getLatestAttempt());
        String contextJson = StringUtils.hasText(task.getLatestContextJson()) ? task.getLatestContextJson() : task.getRequestJson();
        XhsPublishAttemptEntity attempt = XhsPublishAttemptEntity.builder()
                .attemptId(idSupport.nextAttemptId())
                .taskId(task.getTaskId())
                .attemptNo(attemptNo)
                .triggerType(safeTriggerType(dynamicContext.getTriggerType(), dynamicContext.isRetryMode() ? "retry" : "submit"))
                .attemptStatus(PublishAttemptStatusVO.created.name())
                .stage(PublishStageVO.planning.name())
                .contextJson(contextJson)
                .retryable(1)
                .costAmount(BigDecimal.ZERO)
                .durationMs(0L)
                .build();
        publishRepository.saveAttempt(attempt);
        dynamicContext.setAttempt(publishRepository.queryAttemptByAttemptId(attempt.getAttemptId()));
        dynamicContext.setAssetList(publishRepository.listAssetByTaskId(task.getTaskId()));
        dynamicContext.setSourceContext(JSON.parseObject(contextJson));
        log.info("【小红书发布】B正式发布-attempt创建完成：taskId={}, attemptId={}, attemptNo={}",
                dynamicContext.getTask().getTaskId(),
                dynamicContext.getAttempt().getAttemptId(),
                dynamicContext.getAttempt().getAttemptNo());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return payloadAssembleNode;
    }

    private int nextAttemptNo(String taskId, XhsPublishAttemptEntity latestAttempt) {
        XhsPublishAttemptStatsEntity attemptStats = publishRepository.queryAttemptStatsByTaskId(taskId);
        Integer maxAttemptNo = attemptStats == null ? null : attemptStats.getMaxAttemptNo();
        if (maxAttemptNo == null || maxAttemptNo <= 0) {
            if (latestAttempt == null || latestAttempt.getAttemptNo() == null) {
                return 1;
            }
            return latestAttempt.getAttemptNo() + 1;
        }
        return maxAttemptNo + 1;
    }

    private String safeTriggerType(String triggerType, String defaultValue) {
        return StringUtils.hasText(triggerType) ? triggerType : defaultValue;
    }

}
