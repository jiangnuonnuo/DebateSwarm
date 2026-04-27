package com.dasi.domain.xhspublish.service.execution.snapshot;

import com.dasi.domain.xhspublish.model.entity.XhsPublishSnapshotEntity;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;

public interface IXhsPublishSnapshotManager {

    XhsPublishSnapshotEntity savePayloadSnapshot(XhsPublishExecutionContext executionContext, String publishRequestJson);

    XhsPublishSnapshotEntity saveFailureSnapshot(XhsPublishExecutionContext executionContext, String failureResultJson);

    XhsPublishSnapshotEntity saveResultSnapshot(XhsPublishExecutionContext executionContext, String resultJson);

    void keep(XhsPublishSnapshotEntity snapshotEntity);

    void delete(XhsPublishSnapshotEntity snapshotEntity);

}
