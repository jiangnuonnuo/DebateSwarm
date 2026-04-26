package com.dasi.infrastructure.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishTemplateEntity;
import com.dasi.domain.xhspublish.repository.IXhsPublishTemplateRepository;
import com.dasi.infrastructure.persistent.dao.IAiXhsPublishTemplateDao;
import com.dasi.infrastructure.persistent.po.AiXhsPublishTemplate;
import com.dasi.infrastructure.repository.support.XhsPublishRepositoryConverter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class XhsPublishTemplateRepository implements IXhsPublishTemplateRepository {

    @Resource
    private IAiXhsPublishTemplateDao aiXhsPublishTemplateDao;

    @Override
    public XhsPublishTemplateEntity queryByTemplateId(String templateId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishTemplateDao.queryByTemplateId(templateId), XhsPublishTemplateEntity.class);
    }

    @Override
    public List<XhsPublishTemplateEntity> page(Long userId, String keyword, Integer offset, Integer size) {
        return aiXhsPublishTemplateDao.page(userId, keyword, offset, size).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishTemplateEntity.class))
                .toList();
    }

    @Override
    public Integer count(Long userId, String keyword) {
        Integer total = aiXhsPublishTemplateDao.count(userId, keyword);
        return total == null ? 0 : total;
    }

    @Override
    public void insert(XhsPublishTemplateEntity entity) {
        aiXhsPublishTemplateDao.insert(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishTemplate.class));
    }

    @Override
    public void update(XhsPublishTemplateEntity entity) {
        aiXhsPublishTemplateDao.update(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishTemplate.class));
    }

}
