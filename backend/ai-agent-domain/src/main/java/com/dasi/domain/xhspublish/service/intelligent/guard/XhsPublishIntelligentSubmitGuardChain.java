package com.dasi.domain.xhspublish.service.intelligent.guard;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class XhsPublishIntelligentSubmitGuardChain {

    private final List<IXhsPublishIntelligentSubmitGuard> guardList;

    public XhsPublishIntelligentSubmitGuardChain(List<IXhsPublishIntelligentSubmitGuard> guardList) {
        this.guardList = guardList.stream()
                .sorted(Comparator.comparingInt(IXhsPublishIntelligentSubmitGuard::getOrder))
                .toList();
    }

    public void validate(IntelligentSubmitGuardContext context) {
        for (IXhsPublishIntelligentSubmitGuard guard : guardList) {
            guard.validate(context);
        }
    }

}
