package com.dasi.domain.xhspublish.service.rule;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class XhsPublishPayloadRuleChain {

    private final List<IXhsPublishPayloadRule> ruleList;

    public XhsPublishPayloadRuleChain(List<IXhsPublishPayloadRule> ruleList) {
        this.ruleList = ruleList.stream()
                .sorted(Comparator.comparingInt(IXhsPublishPayloadRule::getOrder))
                .toList();
    }

    public void apply(XhsPublishRuleContext context) {
        for (IXhsPublishPayloadRule rule : ruleList) {
            rule.apply(context);
        }
    }

}
