package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiXhsPublishReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IAiXhsPublishReviewDao {
    AiXhsPublishReview queryByReviewId(@Param("reviewId") String reviewId);

    List<AiXhsPublishReview> listByTaskId(@Param("taskId") String taskId);

    void insert(AiXhsPublishReview po);

    void update(AiXhsPublishReview po);
}
