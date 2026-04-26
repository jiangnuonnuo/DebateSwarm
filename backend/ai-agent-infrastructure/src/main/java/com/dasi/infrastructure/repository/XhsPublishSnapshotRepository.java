package com.dasi.infrastructure.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishSnapshotEntity;
import com.dasi.domain.xhspublish.repository.IXhsPublishSnapshotRepository;
import com.dasi.infrastructure.persistent.dao.IAiXhsPublishSnapshotDao;
import com.dasi.infrastructure.persistent.po.AiXhsPublishSnapshot;
import com.dasi.infrastructure.repository.support.XhsPublishRepositoryConverter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class XhsPublishSnapshotRepository implements IXhsPublishSnapshotRepository {

    @Resource
    private IAiXhsPublishSnapshotDao aiXhsPublishSnapshotDao;

    @Override
    public XhsPublishSnapshotEntity queryBySnapshotId(String snapshotId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishSnapshotDao.queryBySnapshotId(snapshotId), XhsPublishSnapshotEntity.class);
    }

    @Override
    public List<XhsPublishSnapshotEntity> listByTaskId(String taskId) {
        return aiXhsPublishSnapshotDao.listByTaskId(taskId).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishSnapshotEntity.class))
                .toList();
    }

    @Override
    public List<XhsPublishSnapshotEntity> listExpired(LocalDateTime expireTime) {
        return aiXhsPublishSnapshotDao.listExpired(expireTime).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishSnapshotEntity.class))
                .toList();
    }

    @Override
    public void insert(XhsPublishSnapshotEntity entity) {
        aiXhsPublishSnapshotDao.insert(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishSnapshot.class));
    }

    @Override
    public void update(XhsPublishSnapshotEntity entity) {
        aiXhsPublishSnapshotDao.update(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishSnapshot.class));
    }

}
