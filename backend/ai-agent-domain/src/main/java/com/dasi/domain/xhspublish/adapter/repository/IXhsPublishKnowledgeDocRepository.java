package com.dasi.domain.xhspublish.adapter.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishKnowledgeDocEntity;

import java.util.List;

public interface IXhsPublishKnowledgeDocRepository {

    XhsPublishKnowledgeDocEntity queryByKnowledgeId(String knowledgeId);

    List<XhsPublishKnowledgeDocEntity> page(Long userId, String knowledgeScope, Integer offset, Integer size);

    Integer count(Long userId, String knowledgeScope);

    void insert(XhsPublishKnowledgeDocEntity entity);

    void update(XhsPublishKnowledgeDocEntity entity);

}

