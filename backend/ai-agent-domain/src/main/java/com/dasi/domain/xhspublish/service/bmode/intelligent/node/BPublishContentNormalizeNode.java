package com.dasi.domain.xhspublish.service.bmode.intelligent.node;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.bmode.context.BPublishIntelligentContext;
import com.dasi.domain.xhspublish.service.generator.GeneratedPublishContext;
import com.dasi.domain.xhspublish.service.rule.XhsPublishRuleSupport;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;

@Slf4j
@Service("bPublishContentNormalizeNode")
public class BPublishContentNormalizeNode extends AbstractBPublishIntelligentNode {

    @Resource
    private BPublishContextPersistNode contextPersistNode;

    @Override
    protected String doApply(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) throws Exception {

        String publishContent = buildLatestContextJson(dynamicContext.getGeneratedPublishContext(), dynamicContext);

        dynamicContext.setLatestContextJson(publishContent);
        log.info("【小红书发布】B智能预处理-上下文组装完成：taskId={}", dynamicContext.getTaskId());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<BPublishIntelligentContext, BPublishIntelligentContext, String> get(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) {
        return contextPersistNode;
    }

    private String buildLatestContextJson(GeneratedPublishContext generatedContext, BPublishIntelligentContext intelligentContext) {
        LinkedHashMap<String, Object> contextMap = new LinkedHashMap<>();
        contextMap.put("title", generatedContext.getTitle());
        contextMap.put("content", generatedContext.getContent());
        contextMap.put("tags", generatedContext.getTags());
        contextMap.put("selectedAssetIds", intelligentContext.getSelectedAssetIds());
        contextMap.put("visibility", resolveVisibility(intelligentContext.getCommand().getVisibility(), generatedContext.getVisibility()));
        contextMap.put("is_original", resolveIsOriginal(intelligentContext.getCommand().getIsOriginal(), generatedContext.getIsOriginal()));
        String scheduleAt = resolveScheduleAt(intelligentContext.getCommand().getScheduledPublishAt());
        if (scheduleAt != null) {
            contextMap.put("schedule_at", scheduleAt);
        }

        JSONObject ext = new JSONObject(new LinkedHashMap<>());
        ext.put("submitMode", "intelligent_submit");
        ext.put("clientId", intelligentContext.getCommand().getClientId());
        ext.put("publishRequirement", intelligentContext.getCommand().getPublishRequirement().trim());
        ext.put("useMcpDefaultAccount", !StringUtils.hasText(intelligentContext.getCommand().getBindingId()));
        contextMap.put("ext", ext);
        return JSON.toJSONString(contextMap);
    }

    private String resolveVisibility(String requestVisibility, String generatedVisibility) {
        if (StringUtils.hasText(requestVisibility)) {
            return XhsPublishRuleSupport.normalizeVisibility(requestVisibility);
        }
        if (StringUtils.hasText(generatedVisibility)) {
            return XhsPublishRuleSupport.normalizeVisibility(generatedVisibility);
        }
        return "公开可见";
    }

    private boolean resolveIsOriginal(Boolean requestIsOriginal, Boolean generatedIsOriginal) {
        if (requestIsOriginal != null) {
            return requestIsOriginal;
        }
        if (generatedIsOriginal != null) {
            return generatedIsOriginal;
        }
        return false;
    }

    private String resolveScheduleAt(java.time.LocalDateTime scheduledPublishAt) {
        if (scheduledPublishAt == null) {
            return null;
        }
        ZoneOffset offset = OffsetDateTime.now().getOffset();
        return OffsetDateTime.of(scheduledPublishAt, offset).toString();
    }

}
