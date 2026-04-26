package com.dasi.infrastructure.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.repository.IXhsPublishAttemptRepository;
import com.dasi.infrastructure.persistent.dao.IAiXhsPublishAttemptDao;
import com.dasi.infrastructure.persistent.po.AiXhsPublishAttempt;
import com.dasi.infrastructure.repository.support.XhsPublishRepositoryConverter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class XhsPublishAttemptRepository implements IXhsPublishAttemptRepository {

    @Resource
    private IAiXhsPublishAttemptDao aiXhsPublishAttemptDao;

    @Override
    public XhsPublishAttemptEntity queryByAttemptId(String attemptId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishAttemptDao.queryByAttemptId(attemptId), XhsPublishAttemptEntity.class);
    }

    @Override
    public List<XhsPublishAttemptEntity> listByTaskId(String taskId) {
        return aiXhsPublishAttemptDao.listByTaskId(taskId).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishAttemptEntity.class))
                .toList();
    }

    @Override
    public void insert(XhsPublishAttemptEntity entity) {
        aiXhsPublishAttemptDao.insert(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishAttempt.class));
    }

    @Override
    public void update(XhsPublishAttemptEntity entity) {
        aiXhsPublishAttemptDao.update(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishAttempt.class));
    }

}
