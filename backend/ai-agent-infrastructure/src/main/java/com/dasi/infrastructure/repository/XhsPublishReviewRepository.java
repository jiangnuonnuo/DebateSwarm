package com.dasi.infrastructure.repository;

import com.dasi.domain.xhspublish.model.entity.XhsPublishReviewEntity;
import com.dasi.domain.xhspublish.repository.IXhsPublishReviewRepository;
import com.dasi.infrastructure.persistent.dao.IAiXhsPublishReviewDao;
import com.dasi.infrastructure.persistent.po.AiXhsPublishReview;
import com.dasi.infrastructure.repository.support.XhsPublishRepositoryConverter;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class XhsPublishReviewRepository implements IXhsPublishReviewRepository {

    @Resource
    private IAiXhsPublishReviewDao aiXhsPublishReviewDao;

    @Override
    public XhsPublishReviewEntity queryByReviewId(String reviewId) {
        return XhsPublishRepositoryConverter.copy(aiXhsPublishReviewDao.queryByReviewId(reviewId), XhsPublishReviewEntity.class);
    }

    @Override
    public List<XhsPublishReviewEntity> listByTaskId(String taskId) {
        return aiXhsPublishReviewDao.listByTaskId(taskId).stream()
                .map(item -> XhsPublishRepositoryConverter.copy(item, XhsPublishReviewEntity.class))
                .toList();
    }

    @Override
    public void insert(XhsPublishReviewEntity entity) {
        aiXhsPublishReviewDao.insert(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishReview.class));
    }

    @Override
    public void update(XhsPublishReviewEntity entity) {
        aiXhsPublishReviewDao.update(XhsPublishRepositoryConverter.copy(entity, AiXhsPublishReview.class));
    }

}
