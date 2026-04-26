package com.dasi.domain.xhspublish.service.rule;

import com.dasi.types.exception.WorkException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PublishTitlePayloadRule implements IXhsPublishPayloadRule {

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public void apply(XhsPublishRuleContext context) {
        String title = XhsPublishRuleSupport.readString(context.getSourceContext(), "title");
        if (!StringUtils.hasText(title)) {
            throw new WorkException("标题不能为空");
        }
        if (title.length() > 20) {
            throw new WorkException("标题长度不能超过 20 个字符");
        }
        context.getPublishPayload().put("title", title);
    }

}
