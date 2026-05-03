package com.dasi.domain.xhspublish.service.bmode.publish.node;

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
@Service("bPublishMcpSubmitNode")
public class BPublishMcpSubmitNode extends AbstractBPublishExecuteNode {

    @Resource
    private IXhsPublishExecutionLifecycle executionLifecycle;

    @Resource
    private IXhsPublishRemoteExecutor remoteExecutor;

    @Resource
    private IXhsPublishAgentFallbackService agentFallbackService;

    @Resource
    private BPublishMcpResultRouteNode mcpResultRouteNode;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        // 第 6 步：远端提交节点只处理远端调用与兜底分流，不再把逻辑压到 remote support。
        executionLifecycle.markPublishSubmitted(dynamicContext);
        long remoteStart = System.currentTimeMillis();
        String clientId = resolveClientId(dynamicContext.getSourceContext());
        XhsPublishRemoteResult primaryResult = remoteExecutor.executeImagePublish(dynamicContext.getPublishRequestJson());
        if (shouldAgentFallback(primaryResult, clientId)) {
            log.warn("【小红书发布】event=agent_fallback_start taskId={} attemptId={} errorCode={}",
                    dynamicContext.getTask().getTaskId(),
                    dynamicContext.getAttempt().getAttemptId(),
                    primaryResult == null ? "-" : primaryResult.getErrorCode());
            dynamicContext.setRemoteResult(agentFallbackService.execute(
                    clientId,
                    dynamicContext.getPublishRequestJson(),
                    dynamicContext.getTask().getTaskId(),
                    dynamicContext.getAttempt().getAttemptId(),
                    dynamicContext.getSourceContext(),
                    dynamicContext.getAssetList()));
        } else {
            dynamicContext.setRemoteResult(primaryResult);
        }
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
        return mcpResultRouteNode;
    }

    private boolean shouldAgentFallback(XhsPublishRemoteResult remoteResult, String clientId) {
        if (remoteResult == null || !remoteResult.isTransientFailure()) {
            return false;
        }
        if (!StringUtils.hasText(clientId)) {
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

    private String resolveClientId(JSONObject sourceContext) {
        if (sourceContext == null) {
            return null;
        }
        JSONObject ext = sourceContext.getJSONObject("ext");
        return ext == null ? null : ext.getString("clientId");
    }

}
