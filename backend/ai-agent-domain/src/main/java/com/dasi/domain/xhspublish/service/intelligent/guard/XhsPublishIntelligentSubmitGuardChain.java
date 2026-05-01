package com.dasi.domain.xhspublish.service.intelligent.guard;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class XhsPublishIntelligentSubmitGuardChain {

    private final List<IXhsPublishIntelligentSubmitGuard> guardList;

    public XhsPublishIntelligentSubmitGuardChain(List<IXhsPublishIntelligentSubmitGuard> guardList) {
        // 一键提交流程的前置守卫链固定按 order 排序，当前最小链路只保留：
        // 1. ClientGuard
        // 2. RequirementGuard
        // 3. ImageSourceGuard
        this.guardList = guardList.stream()
                .sorted(Comparator.comparingInt(IXhsPublishIntelligentSubmitGuard::getOrder))
                .toList();
    }

    public void validate(IntelligentSubmitGuardContext context) {
        // guard 链只负责 fail-fast，不负责写库、不负责生成、不负责提交。
        for (IXhsPublishIntelligentSubmitGuard guard : guardList) {
            guard.validate(context);
        }
    }

}
