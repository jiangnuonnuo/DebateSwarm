package com.dasi.domain.xhspublish.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;

import java.util.List;

public interface IXhsPublishAttemptRepository {

    XhsPublishAttemptEntity queryByAttemptId(String attemptId);

    List<XhsPublishAttemptEntity> listByTaskId(String taskId);

    void insert(XhsPublishAttemptEntity entity);

    void update(XhsPublishAttemptEntity entity);

}
