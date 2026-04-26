package com.dasi.infrastructure.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.repository.IXhsPublishTaskRepository;
import com.dasi.infrastructure.persistent.dao.IAiXhsPublishTaskDao;
import com.dasi.infrastructure.persistent.po.AiXhsPublishTask;
import com.dasi.infrastructure.repository.support.XhsPublishRepositoryConverter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class XhsPublishTaskRepository implements IXhsPublishTaskRepository {

    @Resource
    private IAiXhsPublishTaskDao aiXhsPublishTaskDao;

    @Override
    public XhsPublishTaskEntity queryByTaskId(String taskId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishTaskDao.queryByTaskId(taskId), XhsPublishTaskEntity.class);
    }

    @Override
    public List<XhsPublishTaskEntity> page(Long userId, String keyword, String taskStatus, String currentStage, Integer offset, Integer size) {
        return aiXhsPublishTaskDao.page(userId, keyword, taskStatus, currentStage, offset, size).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishTaskEntity.class))
                .toList();
    }

    @Override
    public Integer count(Long userId, String keyword, String taskStatus, String currentStage) {
        Integer total = aiXhsPublishTaskDao.count(userId, keyword, taskStatus, currentStage);
        return total == null ? 0 : total;
    }

    @Override
    public void insert(XhsPublishTaskEntity entity) {
        aiXhsPublishTaskDao.insert(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishTask.class));
    }

    @Override
    public void update(XhsPublishTaskEntity entity) {
        aiXhsPublishTaskDao.update(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishTask.class));
    }

}
