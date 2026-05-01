package com.dasi.domain.xhspublish.service.intelligent.guard;

import com.dasi.types.exception.WorkException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RequirementGuard implements IXhsPublishIntelligentSubmitGuard {

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public void validate(IntelligentSubmitGuardContext context) {
        if (!StringUtils.hasText(context.getRequest().getPublishRequirement())) {
            throw new WorkException("发布需求不能为空");
        }
    }

}
