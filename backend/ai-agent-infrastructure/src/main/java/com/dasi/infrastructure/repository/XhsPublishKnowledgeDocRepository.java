package com.dasi.infrastructure.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishKnowledgeDocEntity;
import com.dasi.domain.xhspublish.repository.IXhsPublishKnowledgeDocRepository;
import com.dasi.infrastructure.persistent.dao.IAiXhsPublishKnowledgeDocDao;
import com.dasi.infrastructure.persistent.po.AiXhsPublishKnowledgeDoc;
import com.dasi.infrastructure.repository.support.XhsPublishRepositoryConverter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class XhsPublishKnowledgeDocRepository implements IXhsPublishKnowledgeDocRepository {

    @Resource
    private IAiXhsPublishKnowledgeDocDao aiXhsPublishKnowledgeDocDao;

    @Override
    public XhsPublishKnowledgeDocEntity queryByKnowledgeId(String knowledgeId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishKnowledgeDocDao.queryByKnowledgeId(knowledgeId), XhsPublishKnowledgeDocEntity.class);
    }

    @Override
    public List<XhsPublishKnowledgeDocEntity> page(Long userId, String knowledgeScope, Integer offset, Integer size) {
        return aiXhsPublishKnowledgeDocDao.page(userId, knowledgeScope, offset, size).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishKnowledgeDocEntity.class))
                .toList();
    }

    @Override
    public Integer count(Long userId, String knowledgeScope) {
        Integer total = aiXhsPublishKnowledgeDocDao.count(userId, knowledgeScope);
        return total == null ? 0 : total;
    }

    @Override
    public void insert(XhsPublishKnowledgeDocEntity entity) {
        aiXhsPublishKnowledgeDocDao.insert(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishKnowledgeDoc.class));
    }

    @Override
    public void update(XhsPublishKnowledgeDocEntity entity) {
        aiXhsPublishKnowledgeDocDao.update(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishKnowledgeDoc.class));
    }

}
