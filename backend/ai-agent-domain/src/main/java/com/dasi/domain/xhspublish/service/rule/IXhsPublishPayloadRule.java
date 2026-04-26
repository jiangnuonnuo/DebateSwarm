package com.dasi.domain.xhspublish.service.rule;

public interface IXhsPublishPayloadRule {

    int getOrder();

    void apply(XhsPublishRuleContext context);

}
