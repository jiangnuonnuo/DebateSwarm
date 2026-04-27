package com.dasi.domain.xhspublish.service.execution.payload;

import com.alibaba.fastjson2.JSON;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.domain.IPublishValidationDomainService;
import com.dasi.domain.xhspublish.service.rule.XhsPublishPayloadRuleChain;
import com.dasi.domain.xhspublish.service.rule.XhsPublishRuleContext;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class RuleChainXhsPublishPayloadBuilder implements IXhsPublishPayloadBuilder {

    @Resource
    private XhsPublishPayloadRuleChain payloadRuleChain;

    @Resource
    private IPublishValidationDomainService validationDomainService;

    @Override
    public String build(XhsPublishExecutionContext executionContext) {
        XhsPublishRuleContext ruleContext = XhsPublishRuleContext.builder()
                .task(executionContext.getTask())
                .attempt(executionContext.getAttempt())
                .binding(executionContext.getBinding())
                .assetList(executionContext.getAssetList())
                .sourceContext(executionContext.getSourceContext())
                .build();
        payloadRuleChain.apply(ruleContext);

        String publishRequestJson = JSON.toJSONString(ruleContext.getPublishPayload());
        validationDomainService.validatePublishPayload(executionContext.getTask().getPublishType(), publishRequestJson);
        return publishRequestJson;
    }

}
