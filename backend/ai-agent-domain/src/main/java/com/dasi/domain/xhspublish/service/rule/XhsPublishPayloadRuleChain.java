package com.dasi.domain.xhspublish.service.rule;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class XhsPublishPayloadRuleChain {

    private final List<IXhsPublishPayloadRule> ruleList;

    public XhsPublishPayloadRuleChain(List<IXhsPublishPayloadRule> ruleList) {
        // payload 规则链按 order 固定排序；后续只需要新增规则实现类，不要把拼装逻辑塞回 service/controller。
        this.ruleList = ruleList.stream()
                .sorted(Comparator.comparingInt(IXhsPublishPayloadRule::getOrder))
                .toList();
    }

    public void apply(XhsPublishRuleContext context) {
        // 当前规则链职责是“把 sourceContext + assetList 收敛为 publishPayload”。
        // 典型顺序为：账号绑定 -> 标题 -> 正文 -> 图片 -> 可见性 -> 定时 -> 可选字段。
        for (IXhsPublishPayloadRule rule : ruleList) {
            rule.apply(context);
        }
    }

}
