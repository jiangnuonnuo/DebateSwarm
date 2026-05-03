package com.dasi.infrastructure.dao.xhspublish;

import com.dasi.infrastructure.dao.po.xhspublish.AiXhsPublishReview;
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

