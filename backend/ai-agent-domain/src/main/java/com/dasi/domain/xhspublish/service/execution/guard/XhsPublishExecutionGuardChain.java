package com.dasi.domain.xhspublish.service.execution.guard;

import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class XhsPublishExecutionGuardChain {

    private final List<IXhsPublishExecutionGuard> guardList;

    public XhsPublishExecutionGuardChain(List<IXhsPublishExecutionGuard> guardList) {
        this.guardList = guardList.stream()
                .sorted(Comparator.comparingInt(IXhsPublishExecutionGuard::getOrder))
                .toList();
    }

    public void guard(XhsPublishExecutionContext executionContext) {
        for (IXhsPublishExecutionGuard guard : guardList) {
            guard.guard(executionContext);
        }
    }

}
