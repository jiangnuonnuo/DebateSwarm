package com.dasi.infrastructure.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishContentAssetEntity;
import com.dasi.domain.xhspublish.repository.IXhsPublishContentAssetRepository;
import com.dasi.infrastructure.persistent.dao.IAiXhsPublishContentAssetDao;
import com.dasi.infrastructure.persistent.po.AiXhsPublishContentAsset;
import com.dasi.infrastructure.repository.support.XhsPublishRepositoryConverter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class XhsPublishContentAssetRepository implements IXhsPublishContentAssetRepository {

    @Resource
    private IAiXhsPublishContentAssetDao aiXhsPublishContentAssetDao;

    @Override
    public XhsPublishContentAssetEntity queryByAssetId(String assetId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishContentAssetDao.queryByAssetId(assetId), XhsPublishContentAssetEntity.class);
    }

    @Override
    public List<XhsPublishContentAssetEntity> listByTaskId(String taskId) {
        return aiXhsPublishContentAssetDao.listByTaskId(taskId).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishContentAssetEntity.class))
                .toList();
    }

    @Override
    public List<XhsPublishContentAssetEntity> listByAttemptId(String attemptId) {
        return aiXhsPublishContentAssetDao.listByAttemptId(attemptId).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishContentAssetEntity.class))
                .toList();
    }

    @Override
    public List<XhsPublishContentAssetEntity> listExpired(LocalDateTime expireTime) {
        return aiXhsPublishContentAssetDao.listExpired(expireTime).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishContentAssetEntity.class))
                .toList();
    }

    @Override
    public void insert(XhsPublishContentAssetEntity entity) {
        aiXhsPublishContentAssetDao.insert(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishContentAsset.class));
    }

    @Override
    public void update(XhsPublishContentAssetEntity entity) {
        aiXhsPublishContentAssetDao.update(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishContentAsset.class));
    }

}
