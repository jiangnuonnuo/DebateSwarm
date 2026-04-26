package com.dasi.domain.xhspublish.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishContentAssetEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface IXhsPublishContentAssetRepository {

    XhsPublishContentAssetEntity queryByAssetId(String assetId);

    List<XhsPublishContentAssetEntity> listByTaskId(String taskId);

    List<XhsPublishContentAssetEntity> listByAttemptId(String attemptId);

    List<XhsPublishContentAssetEntity> listExpired(LocalDateTime expireTime);

    void insert(XhsPublishContentAssetEntity entity);

    void update(XhsPublishContentAssetEntity entity);

}
