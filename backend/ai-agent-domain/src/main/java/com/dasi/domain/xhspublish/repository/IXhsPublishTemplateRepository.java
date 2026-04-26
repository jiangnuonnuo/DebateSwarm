package com.dasi.domain.xhspublish.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishTemplateEntity;

import java.util.List;

public interface IXhsPublishTemplateRepository {

    XhsPublishTemplateEntity queryByTemplateId(String templateId);

    List<XhsPublishTemplateEntity> page(Long userId, String keyword, Integer offset, Integer size);

    Integer count(Long userId, String keyword);

    void insert(XhsPublishTemplateEntity entity);

    void update(XhsPublishTemplateEntity entity);

}
