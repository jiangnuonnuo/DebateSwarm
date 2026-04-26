package com.dasi.infrastructure.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishAccountBindingEntity;
import com.dasi.domain.xhspublish.repository.IXhsPublishAccountBindingRepository;
import com.dasi.infrastructure.persistent.dao.IAiXhsPublishAccountBindingDao;
import com.dasi.infrastructure.persistent.po.AiXhsPublishAccountBinding;
import com.dasi.infrastructure.repository.support.XhsPublishRepositoryConverter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

@Repository
public class XhsPublishAccountBindingRepository implements IXhsPublishAccountBindingRepository {

    @Resource
    private IAiXhsPublishAccountBindingDao aiXhsPublishAccountBindingDao;

    @Override
    public XhsPublishAccountBindingEntity queryByBindingId(String bindingId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishAccountBindingDao.queryByBindingId(bindingId), XhsPublishAccountBindingEntity.class);
    }

    @Override
    public XhsPublishAccountBindingEntity queryDefaultByUserId(Long userId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishAccountBindingDao.queryDefaultByUserId(userId), XhsPublishAccountBindingEntity.class);
    }

    @Override
    public void insert(XhsPublishAccountBindingEntity entity) {
        aiXhsPublishAccountBindingDao.insert(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishAccountBinding.class));
    }

    @Override
    public void update(XhsPublishAccountBindingEntity entity) {
        aiXhsPublishAccountBindingDao.update(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishAccountBinding.class));
    }

}
