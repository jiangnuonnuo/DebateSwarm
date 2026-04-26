package com.dasi.domain.xhspublish.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishSnapshotEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface IXhsPublishSnapshotRepository {

    XhsPublishSnapshotEntity queryBySnapshotId(String snapshotId);

    List<XhsPublishSnapshotEntity> listByTaskId(String taskId);

    List<XhsPublishSnapshotEntity> listExpired(LocalDateTime expireTime);

    void insert(XhsPublishSnapshotEntity entity);

    void update(XhsPublishSnapshotEntity entity);

}
