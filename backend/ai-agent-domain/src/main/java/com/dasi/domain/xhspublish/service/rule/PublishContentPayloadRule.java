package com.dasi.domain.xhspublish.service.rule;

import com.dasi.types.exception.WorkException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PublishContentPayloadRule implements IXhsPublishPayloadRule {

    @Override
    public int getOrder() {
        return 30;
    }

    @Override
    public void apply(XhsPublishRuleContext context) {
        String content = XhsPublishRuleSupport.readString(context.getSourceContext(), "content");
        if (!StringUtils.hasText(content)) {
            throw new WorkException("正文不能为空");
        }
        context.getPublishPayload().put("content", content);
    }

}
