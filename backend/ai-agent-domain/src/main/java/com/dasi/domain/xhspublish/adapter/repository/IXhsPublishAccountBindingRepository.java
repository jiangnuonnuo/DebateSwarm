package com.dasi.domain.xhspublish.adapter.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishAccountBindingEntity;

public interface IXhsPublishAccountBindingRepository {

    XhsPublishAccountBindingEntity queryByBindingId(String bindingId);

    XhsPublishAccountBindingEntity queryDefaultByUserId(Long userId);

    void insert(XhsPublishAccountBindingEntity entity);

    void update(XhsPublishAccountBindingEntity entity);

}

