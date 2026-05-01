package com.dasi.domain.xhspublish.service.intelligent.guard;

public interface IXhsPublishIntelligentSubmitGuard {

    int getOrder();

    void validate(IntelligentSubmitGuardContext context);

}
