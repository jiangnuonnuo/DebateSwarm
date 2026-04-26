package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiXhsPublishAttempt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IAiXhsPublishAttemptDao {
    AiXhsPublishAttempt queryByAttemptId(@Param("attemptId") String attemptId);

    List<AiXhsPublishAttempt> listByTaskId(@Param("taskId") String taskId);

    void insert(AiXhsPublishAttempt po);

    void update(AiXhsPublishAttempt po);
}
