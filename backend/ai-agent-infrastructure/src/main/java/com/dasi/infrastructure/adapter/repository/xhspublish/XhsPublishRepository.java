package com.dasi.infrastructure.adapter.repository.xhspublish;

import com.dasi.domain.xhspublish.model.aggregate.XhsPublishTaskAggregate;
import com.dasi.domain.xhspublish.model.entity.*;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.infrastructure.adapter.repository.support.XhsPublishRepositoryConverter;
import com.dasi.infrastructure.dao.po.xhspublish.*;
import com.dasi.infrastructure.dao.xhspublish.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class XhsPublishRepository implements IXhsPublishRepository {

    @Resource
    private IAiXhsPublishTaskDao aiXhsPublishTaskDao;

    @Resource
    private IAiXhsPublishAttemptDao aiXhsPublishAttemptDao;

    @Resource
    private IAiXhsPublishReviewDao aiXhsPublishReviewDao;

    @Resource
    private IAiXhsPublishContentAssetDao aiXhsPublishContentAssetDao;

    @Resource
    private IAiXhsPublishSnapshotDao aiXhsPublishSnapshotDao;

    @Override
    public XhsPublishTaskAggregate queryAggregateByTaskId(String taskId) {
        XhsPublishTaskEntity task = queryTaskByTaskId(taskId);
        if (task == null) {
            return null;
        }
        List<XhsPublishAttemptEntity> attemptList = listAttemptByTaskId(taskId);
        return XhsPublishTaskAggregate.builder()
                .task(task)
                .latestAttempt(attemptList.isEmpty() ? null : attemptList.get(0))
                .attemptList(attemptList)
                .reviewList(listReviewByTaskId(taskId))
                .assetList(listAssetByTaskId(taskId))
                .build();
    }

    @Override
    public XhsPublishTaskEntity queryTaskByTaskId(String taskId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishTaskDao.queryByTaskId(taskId), XhsPublishTaskEntity.class);
    }

    @Override
    public List<XhsPublishTaskEntity> pageTask(Long userId, String keyword, String taskStatus, String currentStage, Integer offset, Integer size) {
        return aiXhsPublishTaskDao.page(userId, keyword, taskStatus, currentStage, offset, size).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishTaskEntity.class))
                .toList();
    }

    @Override
    public Integer countTask(Long userId, String keyword, String taskStatus, String currentStage) {
        Integer total = aiXhsPublishTaskDao.count(userId, keyword, taskStatus, currentStage);
        return total == null ? 0 : total;
    }

    @Override
    public XhsPublishAttemptEntity queryAttemptByAttemptId(String attemptId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishAttemptDao.queryByAttemptId(attemptId), XhsPublishAttemptEntity.class);
    }

    @Override
    public XhsPublishAttemptEntity queryLatestAttemptByTaskId(String taskId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishAttemptDao.queryLatestByTaskId(taskId), XhsPublishAttemptEntity.class);
    }

    @Override
    public XhsPublishAttemptStatsEntity queryAttemptStatsByTaskId(String taskId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishAttemptDao.queryStatsByTaskId(taskId), XhsPublishAttemptStatsEntity.class);
    }

    @Override
    public List<XhsPublishAttemptEntity> listAttemptByTaskId(String taskId) {
        return aiXhsPublishAttemptDao.listByTaskId(taskId).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishAttemptEntity.class))
                .toList();
    }

    @Override
    public XhsPublishReviewEntity queryReviewByReviewId(String reviewId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishReviewDao.queryByReviewId(reviewId), XhsPublishReviewEntity.class);
    }

    @Override
    public List<XhsPublishReviewEntity> listReviewByTaskId(String taskId) {
        return aiXhsPublishReviewDao.listByTaskId(taskId).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishReviewEntity.class))
                .toList();
    }

    @Override
    public XhsPublishContentAssetEntity queryAssetByAssetId(String assetId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishContentAssetDao.queryByAssetId(assetId), XhsPublishContentAssetEntity.class);
    }

    @Override
    public List<XhsPublishContentAssetEntity> listAssetByTaskId(String taskId) {
        return aiXhsPublishContentAssetDao.listByTaskId(taskId).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishContentAssetEntity.class))
                .toList();
    }

    @Override
    public List<XhsPublishContentAssetEntity> listAssetByAttemptId(String attemptId) {
        return aiXhsPublishContentAssetDao.listByAttemptId(attemptId).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishContentAssetEntity.class))
                .toList();
    }

    @Override
    public List<XhsPublishContentAssetEntity> listExpiredAsset(LocalDateTime expireTime) {
        return aiXhsPublishContentAssetDao.listExpired(expireTime).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishContentAssetEntity.class))
                .toList();
    }

    @Override
    public XhsPublishSnapshotEntity querySnapshotBySnapshotId(String snapshotId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishSnapshotDao.queryBySnapshotId(snapshotId), XhsPublishSnapshotEntity.class);
    }

    @Override
    public List<XhsPublishSnapshotEntity> listSnapshotByTaskId(String taskId) {
        return aiXhsPublishSnapshotDao.listByTaskId(taskId).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishSnapshotEntity.class))
                .toList();
    }

    @Override
    public List<XhsPublishSnapshotEntity> listExpiredSnapshot(LocalDateTime expireTime) {
        return aiXhsPublishSnapshotDao.listExpired(expireTime).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishSnapshotEntity.class))
                .toList();
    }

    @Override
    public void saveTask(XhsPublishTaskEntity entity) {
        if (entity.getId() == null) {
            aiXhsPublishTaskDao.insert(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishTask.class));
            return;
        }
        aiXhsPublishTaskDao.update(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishTask.class));
    }

    @Override
    public void saveAttempt(XhsPublishAttemptEntity entity) {
        // attempt 表的 cost_amount 为非空字段；仓储层统一兜底，避免不同入口遗漏默认值。
        if (entity.getCostAmount() == null) {
            entity.setCostAmount(BigDecimal.ZERO);
        }
        // attempt 表的 duration_ms 也用于聚合统计；新建时还没执行完成，统一从 0 起步。
        if (entity.getDurationMs() == null) {
            entity.setDurationMs(0L);
        }
        if (entity.getId() == null) {
            aiXhsPublishAttemptDao.insert(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishAttempt.class));
            return;
        }
        aiXhsPublishAttemptDao.update(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishAttempt.class));
    }

    @Override
    public void saveReview(XhsPublishReviewEntity entity) {
        if (entity.getId() == null) {
            aiXhsPublishReviewDao.insert(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishReview.class));
            return;
        }
        aiXhsPublishReviewDao.update(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishReview.class));
    }

    @Override
    public void saveAsset(XhsPublishContentAssetEntity entity) {
        if (entity.getId() == null) {
            aiXhsPublishContentAssetDao.insert(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishContentAsset.class));
            return;
        }
        aiXhsPublishContentAssetDao.update(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishContentAsset.class));
    }

    @Override
    public void saveSnapshot(XhsPublishSnapshotEntity entity) {
        if (entity.getId() == null) {
            aiXhsPublishSnapshotDao.insert(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishSnapshot.class));
            return;
        }
        aiXhsPublishSnapshotDao.update(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishSnapshot.class));
    }

}

