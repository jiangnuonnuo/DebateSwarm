package com.dasi.domain.xhspublish.service.execution.node;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.execution.fallback.IXhsPublishAgentFallbackService;
import com.dasi.domain.xhspublish.service.execution.lifecycle.IXhsPublishExecutionLifecycle;
import com.dasi.domain.xhspublish.service.execution.remote.IXhsPublishRemoteExecutor;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service("xhsPublishRemoteNode")
public class XhsPublishRemoteNode extends AbstractXhsPublishNode {

    @Resource
    private IXhsPublishExecutionLifecycle executionLifecycle;

    @Resource
    private IXhsPublishRemoteExecutor remoteExecutor;

    @Resource
    private IXhsPublishAgentFallbackService agentFallbackService;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        // 步骤 4：远端节点先把 task/attempt 标记为 publish_submitted，再执行真正的外部发布。
        executionLifecycle.markPublishSubmitted(dynamicContext);
        long remoteStart = System.currentTimeMillis();
        dynamicContext.setRemoteResult(executeRemoteWithFallback(dynamicContext));
        long durationMs = Math.max(0, System.currentTimeMillis() - remoteStart);
        dynamicContext.setRemoteDurationMs(durationMs);
        log.info("【小红书发布】event=remote_called taskId={} attemptId={} errorCode={} durationMs={}",
                dynamicContext.getTask().getTaskId(),
                dynamicContext.getAttempt().getAttemptId(),
                dynamicContext.getRemoteResult() == null ? "-" : dynamicContext.getRemoteResult().getErrorCode(),
                durationMs);
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return getBean("xhsPublishResultNode");
    }

    XhsPublishRemoteResult executeRemoteWithFallback(XhsPublishExecutionContext executionContext) {
        // 步骤 4.1：优先走后端直调 MCP，保持平台主执行链稳定且可审计。
        XhsPublishRemoteResult primaryResult = remoteExecutor.executeImagePublish(executionContext.getPublishRequestJson());
        if (!shouldAgentFallback(primaryResult, executionContext)) {
            return primaryResult;
        }

        // 步骤 4.2：只有 transient 且没有有效 job_id 的失败，才允许单次 Agent fallback。
        log.warn("【小红书发布】event=agent_fallback_start taskId={} attemptId={} errorCode={}",
                executionContext.getTask().getTaskId(),
                executionContext.getAttempt().getAttemptId(),
                primaryResult.getErrorCode());
        return agentFallbackService.execute(executionContext);
    }

    boolean shouldAgentFallback(XhsPublishRemoteResult remoteResult, XhsPublishExecutionContext executionContext) {
        // fallback 判定顺序固定：
        // 1. 必须是 failed + transient
        // 2. 必须能从 ext.clientId 找到可用发布 client
        // 3. 必须还没有拿到 job_id，避免和已受理的远端任务重复执行
        // 4. 错误码/消息必须属于连接、初始化、405、timeout、unavailable 等基础设施故障
        if (remoteResult == null || !remoteResult.isTransientFailure()) {
            return false;
        }
        if (!StringUtils.hasText(resolveClientId(executionContext))) {
            return false;
        }
        if (hasJobId(remoteResult)) {
            return false;
        }
        String errorCode = StringUtils.hasText(remoteResult.getErrorCode()) ? remoteResult.getErrorCode().trim().toUpperCase() : "";
        String errorMessage = StringUtils.hasText(remoteResult.getErrorMessage()) ? remoteResult.getErrorMessage().trim().toUpperCase() : "";
        return "REMOTE_CALL_EXCEPTION".equals(errorCode)
                || errorCode.contains("TIMEOUT")
                || errorCode.contains("NETWORK")
                || errorCode.contains("UNAVAILABLE")
                || errorCode.contains("405")
                || errorMessage.contains("TIMEOUT")
                || errorMessage.contains("NETWORK")
                || errorMessage.contains("UNAVAILABLE")
                || errorMessage.contains("405")
                || errorMessage.contains("INITIALIZE")
                || errorMessage.contains("CONNECT");
    }

    private boolean hasJobId(XhsPublishRemoteResult remoteResult) {
        // 一旦已有 job_id，就说明远端已经受理或至少创建了任务，此时不允许再兜底重复发布。
        if (remoteResult == null || !StringUtils.hasText(remoteResult.getResultJson())) {
            return false;
        }
        JSONObject object = JSON.parseObject(remoteResult.getResultJson());
        return object != null && StringUtils.hasText(object.getString("job_id"));
    }

    private String resolveClientId(XhsPublishExecutionContext executionContext) {
        // clientId 固定存放在 latestContextJson.ext.clientId，直调和兜底共用这一个来源。
        if (executionContext == null || executionContext.getSourceContext() == null) {
            return null;
        }
        JSONObject ext = executionContext.getSourceContext().getJSONObject("ext");
        return ext == null ? null : ext.getString("clientId");
    }

}
