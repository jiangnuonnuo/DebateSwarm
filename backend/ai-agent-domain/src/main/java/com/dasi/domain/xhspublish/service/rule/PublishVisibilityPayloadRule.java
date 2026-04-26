package com.dasi.domain.xhspublish.service.rule;

import org.springframework.stereotype.Component;

@Component
public class PublishVisibilityPayloadRule implements IXhsPublishPayloadRule {

    @Override
    public int getOrder() {
        return 60;
    }

    @Override
    public void apply(XhsPublishRuleContext context) {
        String visibility = XhsPublishRuleSupport.readString(context.getSourceContext(), "visibility");
        context.getPublishPayload().put("visibility", XhsPublishRuleSupport.normalizeVisibility(visibility));
    }

}
