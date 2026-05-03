package com.dasi.domain.xhspublish.service.bmode.publish.node;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAccountBindingEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishContentAssetEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.domain.IPublishValidationDomainService;
import com.dasi.domain.xhspublish.service.execution.lifecycle.IXhsPublishExecutionLifecycle;
import com.dasi.domain.xhspublish.service.execution.snapshot.IXhsPublishSnapshotManager;
import com.dasi.domain.xhspublish.service.rule.XhsPublishRuleSupport;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service("bPublishPayloadAssembleNode")
public class BPublishPayloadAssembleNode extends AbstractBPublishExecuteNode {

    @Resource
    private IXhsPublishExecutionLifecycle executionLifecycle;

    @Resource
    private IPublishValidationDomainService validationDomainService;

    @Resource
    private IXhsPublishSnapshotManager snapshotManager;

    @Resource
    private BPublishMcpSubmitNode mcpSubmitNode;

    @Override
    protected String doApply(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) throws Exception {
        // 第 5 步：在 payload 节点完成参数组装与硬规则校验，不再通过 support 吃整包上下文。
        executionLifecycle.markExecutionStarted(dynamicContext, dynamicContext.getStartTime());
        String publishRequestJson = buildPayload(
                dynamicContext.getTask(),
                dynamicContext.getBinding(),
                dynamicContext.getSourceContext(),
                dynamicContext.getAssetList());
        dynamicContext.setPublishRequestJson(publishRequestJson);
        executionLifecycle.markPayloadBuilt(dynamicContext, publishRequestJson);
        dynamicContext.setPayloadSnapshot(snapshotManager.savePayloadSnapshot(dynamicContext, publishRequestJson));
        log.info("【小红书发布】B正式发布-payload组装完成：taskId={}, attemptId={}",
                dynamicContext.getTask().getTaskId(),
                dynamicContext.getAttempt().getAttemptId());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<XhsPublishExecutionContext, XhsPublishExecutionContext, String> get(XhsPublishExecutionContext requestParameter, XhsPublishExecutionContext dynamicContext) {
        return mcpSubmitNode;
    }

    private String buildPayload(XhsPublishTaskEntity task,
                                XhsPublishAccountBindingEntity binding,
                                JSONObject sourceContext,
                                List<XhsPublishContentAssetEntity> assetList) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("task_id", task.getTaskId());
        payload.put("mode", "async");

        if (!useMcpDefaultAccount(sourceContext)) {
            if (binding == null) {
                throw new WorkException("发布账号绑定不能为空");
            }
            if (!StringUtils.hasText(binding.getMcpTenantId()) || !StringUtils.hasText(binding.getMcpAccountId())) {
                throw new WorkException("发布账号绑定缺少 MCP 路由信息");
            }
            payload.put("tenant_id", binding.getMcpTenantId());
            payload.put("account_id", binding.getMcpAccountId());
        }

        String title = XhsPublishRuleSupport.readString(sourceContext, "title");
        if (!StringUtils.hasText(title)) {
            throw new WorkException("标题不能为空");
        }
        if (title.length() > 20) {
            throw new WorkException("标题长度不能超过 20 个字符");
        }
        payload.put("title", title);

        String content = XhsPublishRuleSupport.readString(sourceContext, "content");
        if (!StringUtils.hasText(content)) {
            throw new WorkException("正文不能为空");
        }
        payload.put("content", content);

        List<String> images = XhsPublishRuleSupport.resolveImages(sourceContext, assetList);
        if (images == null || images.isEmpty()) {
            throw new WorkException("图文发布至少需要 1 张图片，请先上传图片或在上下文中提供 images");
        }
        payload.put("images", images);

        String scheduleAt = XhsPublishRuleSupport.readString(sourceContext, "schedule_at", "scheduledPublishAt", "scheduled_publish_at");
        String normalizedSchedule = XhsPublishRuleSupport.normalizeSchedule(scheduleAt);
        if (normalizedSchedule != null) {
            payload.put("schedule_at", normalizedSchedule);
        }

        payload.put("visibility", XhsPublishRuleSupport.normalizeVisibility(XhsPublishRuleSupport.readString(sourceContext, "visibility")));
        payload.put("tags", XhsPublishRuleSupport.readStringList(sourceContext, "tags"));
        payload.put("products", XhsPublishRuleSupport.readStringList(sourceContext, "products"));
        payload.put("is_original", XhsPublishRuleSupport.readBoolean(sourceContext, "is_original", false));

        String batchId = XhsPublishRuleSupport.readString(sourceContext, "batch_id");
        if (batchId != null) {
            payload.put("batch_id", batchId);
        }

        String publishRequestJson = JSON.toJSONString(payload);
        validationDomainService.validatePublishPayload(task.getPublishType(), publishRequestJson);
        return publishRequestJson;
    }

    private boolean useMcpDefaultAccount(JSONObject sourceContext) {
        if (sourceContext == null) {
            return false;
        }
        JSONObject ext = sourceContext.getJSONObject("ext");
        return ext != null && ext.getBooleanValue("useMcpDefaultAccount");
    }

}
