package com.dasi.domain.xhspublish.service.bmode.publish.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptStatsEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.model.valobj.PublishAttemptStatusVO;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.domain.IPublishRetryDomainService;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionContext;
import com.dasi.domain.xhspublish.service.domain.retry.RetryDecisionResult;
import com.dasi.domain.xhspublish.service.support.RetryDecisionMessageSupport;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service("bPublishPreflightValidateNode")
public class BPublishPreflightValidateNode extends AbstractBPublishExecuteNode {

    @Resource
    private IXhsPublishRepository publishRepository;

    @Resource
    private IPublishRetryDomainService retryDomainService;

    @Resource
    private BPublishAttemptCreateNode attemptCreateNode;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        // 第 3 步：提交前校验直接放在校验节点，不再外包给 support.execute(context)。
        XhsPublishTaskEntity task = dynamicContext.getTask();
        if (dynamicContext.isRetryMode()) {
            XhsPublishAttemptEntity latestAttempt = publishRepository.queryLatestAttemptByTaskId(task.getTaskId());
            if (latestAttempt == null) {
                throw new WorkException("当前任务暂无可重试的执行记录");
            }
            dynamicContext.setLatestAttempt(latestAttempt);
            XhsPublishAttemptStatsEntity attemptStats = publishRepository.queryAttemptStatsByTaskId(task.getTaskId());
            RetryDecisionResult retryDecision = retryDomainService.evaluate(RetryDecisionContext.builder()
                    .retryPolicyJson(task.getRetryPolicyJson())
                    .latestAttemptNo(latestAttempt.getAttemptNo())
                    .latestErrorCode(latestAttempt.getErrorCode())
                    .totalDurationMs(attemptStats == null ? 0L : attemptStats.getTotalDurationMs())
                    .totalCostAmount(attemptStats == null ? BigDecimal.ZERO : attemptStats.getTotalCostAmount())
                    .activeAttempt(isActiveAttempt(latestAttempt))
                    .build());
            if (!retryDecision.isAllowed()) {
                throw new WorkException(RetryDecisionMessageSupport.toUserMessage(retryDecision));
            }
        } else {
            ensureNoActiveAttempt(publishRepository.listAttemptByTaskId(task.getTaskId()));
        }
        log.info("【小红书发布】B正式发布-前置校验通过：taskId={}, retryMode={}",
                dynamicContext.getTask().getTaskId(), dynamicContext.isRetryMode());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return attemptCreateNode;
    }

    private boolean isActiveAttempt(XhsPublishAttemptEntity latestAttempt) {
        if (latestAttempt == null || !StringUtils.hasText(latestAttempt.getAttemptStatus())) {
            return false;
        }
        String status = latestAttempt.getAttemptStatus();
        return PublishAttemptStatusVO.created.name().equals(status)
                || PublishAttemptStatusVO.running.name().equals(status)
                || PublishAttemptStatusVO.waiting_review.name().equals(status)
                || PublishAttemptStatusVO.accepted.name().equals(status);
    }

    private void ensureNoActiveAttempt(List<XhsPublishAttemptEntity> attemptList) {
        if (attemptList == null || attemptList.isEmpty()) {
            return;
        }
        if (attemptList.stream().anyMatch(this::isActiveAttempt)) {
            throw new WorkException("任务正在执行中，请勿重复提交");
        }
    }

}
