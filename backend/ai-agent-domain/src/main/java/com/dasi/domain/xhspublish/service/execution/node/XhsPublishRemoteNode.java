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
        // 节点顺序固定：先回写 publish_submitted，再发起远端调用，保证状态与外部交互一致。
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
        XhsPublishRemoteResult primaryResult = remoteExecutor.executeImagePublish(executionContext.getPublishRequestJson());
        if (!shouldAgentFallback(primaryResult, executionContext)) {
            return primaryResult;
        }
        log.warn("【小红书发布】event=agent_fallback_start taskId={} attemptId={} errorCode={}",
                executionContext.getTask().getTaskId(),
                executionContext.getAttempt().getAttemptId(),
                primaryResult.getErrorCode());
        return agentFallbackService.execute(executionContext);
    }

    boolean shouldAgentFallback(XhsPublishRemoteResult remoteResult, XhsPublishExecutionContext executionContext) {
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
        if (remoteResult == null || !StringUtils.hasText(remoteResult.getResultJson())) {
            return false;
        }
        JSONObject object = JSON.parseObject(remoteResult.getResultJson());
        return object != null && StringUtils.hasText(object.getString("job_id"));
    }

    private String resolveClientId(XhsPublishExecutionContext executionContext) {
        if (executionContext == null || executionContext.getSourceContext() == null) {
            return null;
        }
        JSONObject ext = executionContext.getSourceContext().getJSONObject("ext");
        return ext == null ? null : ext.getString("clientId");
    }

}
