package com.dasi.domain.xhspublish.service.intelligent.guard;

import com.dasi.domain.xhspublish.service.support.XhsPublishClientSupport;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class ClientGuard implements IXhsPublishIntelligentSubmitGuard {

    @Resource
    private XhsPublishClientSupport clientSupport;

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public void validate(IntelligentSubmitGuardContext context) {
        clientSupport.ensureClientAvailable(context.getRequest().getClientId());
    }

}
