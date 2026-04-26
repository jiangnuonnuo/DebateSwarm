package com.dasi.domain.xhspublish.service.support;

import com.dasi.domain.util.random.IRandomUtil;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class XhsPublishIdSupport {

    @Resource
    private IRandomUtil randomUtil;

    public String nextTaskId() {
        return randomUtil.randomTaskId();
    }

    public String nextAttemptId() {
        return build("xhs_attempt_");
    }

    public String nextReviewId() {
        return build("xhs_review_");
    }

    public String nextTemplateId() {
        return build("xhs_template_");
    }

    public String nextBindingId() {
        return build("xhs_binding_");
    }

    public String nextAssetId() {
        return build("xhs_asset_");
    }

    public String nextKnowledgeId() {
        return build("xhs_knowledge_");
    }

    public String nextSnapshotId() {
        return build("xhs_snapshot_");
    }

    private String build(String prefix) {
        return prefix + randomUtil.uuid().replace("-", "");
    }

}
