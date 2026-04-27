package com.dasi.domain.xhspublish.adapter.repository;

import com.dasi.domain.xhspublish.model.aggregate.XhsPublishTaskAggregate;
import com.dasi.domain.xhspublish.model.entity.*;

import java.time.LocalDateTime;
import java.util.List;

public interface IXhsPublishRepository {

    XhsPublishTaskAggregate queryAggregateByTaskId(String taskId);

    XhsPublishTaskEntity queryTaskByTaskId(String taskId);

    List<XhsPublishTaskEntity> pageTask(Long userId, String keyword, String taskStatus, String currentStage, Integer offset, Integer size);

    Integer countTask(Long userId, String keyword, String taskStatus, String currentStage);

    XhsPublishAttemptEntity queryAttemptByAttemptId(String attemptId);

    List<XhsPublishAttemptEntity> listAttemptByTaskId(String taskId);

    XhsPublishReviewEntity queryReviewByReviewId(String reviewId);

    List<XhsPublishReviewEntity> listReviewByTaskId(String taskId);

    XhsPublishContentAssetEntity queryAssetByAssetId(String assetId);

    List<XhsPublishContentAssetEntity> listAssetByTaskId(String taskId);

    List<XhsPublishContentAssetEntity> listAssetByAttemptId(String attemptId);

    List<XhsPublishContentAssetEntity> listExpiredAsset(LocalDateTime expireTime);

    XhsPublishSnapshotEntity querySnapshotBySnapshotId(String snapshotId);

    List<XhsPublishSnapshotEntity> listSnapshotByTaskId(String taskId);

    List<XhsPublishSnapshotEntity> listExpiredSnapshot(LocalDateTime expireTime);

    void saveTask(XhsPublishTaskEntity entity);

    void saveAttempt(XhsPublishAttemptEntity entity);

    void saveReview(XhsPublishReviewEntity entity);

    void saveAsset(XhsPublishContentAssetEntity entity);

    void saveSnapshot(XhsPublishSnapshotEntity entity);

}

