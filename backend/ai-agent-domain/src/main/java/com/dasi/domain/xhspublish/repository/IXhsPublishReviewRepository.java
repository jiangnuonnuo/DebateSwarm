package com.dasi.domain.xhspublish.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishReviewEntity;

import java.util.List;

public interface IXhsPublishReviewRepository {

    XhsPublishReviewEntity queryByReviewId(String reviewId);

    List<XhsPublishReviewEntity> listByTaskId(String taskId);

    void insert(XhsPublishReviewEntity entity);

    void update(XhsPublishReviewEntity entity);

}
