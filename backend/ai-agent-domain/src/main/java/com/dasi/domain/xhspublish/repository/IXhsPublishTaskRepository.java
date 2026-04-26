package com.dasi.domain.xhspublish.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;

import java.util.List;

public interface IXhsPublishTaskRepository {

    XhsPublishTaskEntity queryByTaskId(String taskId);

    List<XhsPublishTaskEntity> page(Long userId, String keyword, String taskStatus, String currentStage, Integer offset, Integer size);

    Integer count(Long userId, String keyword, String taskStatus, String currentStage);

    void insert(XhsPublishTaskEntity entity);

    void update(XhsPublishTaskEntity entity);

}
