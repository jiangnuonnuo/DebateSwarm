package com.dasi.domain.xhspublish.service.rule;

import org.springframework.stereotype.Component;

@Component
public class PublishOptionalPayloadRule implements IXhsPublishPayloadRule {

    @Override
    public int getOrder() {
        return 70;
    }

    @Override
    public void apply(XhsPublishRuleContext context) {
        context.getPublishPayload().put("tags", XhsPublishRuleSupport.readStringList(context.getSourceContext(), "tags"));
        context.getPublishPayload().put("products", XhsPublishRuleSupport.readStringList(context.getSourceContext(), "products"));
        context.getPublishPayload().put("is_original", XhsPublishRuleSupport.readBoolean(context.getSourceContext(), "is_original", false));
        String batchId = XhsPublishRuleSupport.readString(context.getSourceContext(), "batch_id");
        if (batchId != null) {
            context.getPublishPayload().put("batch_id", batchId);
        }
    }

}
