package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiXhsPublishAttempt;
import com.dasi.infrastructure.persistent.po.AiXhsPublishAttemptStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface IAiXhsPublishAttemptDao {
    AiXhsPublishAttempt queryByAttemptId(@Param("attemptId") String attemptId);

    AiXhsPublishAttempt queryLatestByTaskId(@Param("taskId") String taskId);

    AiXhsPublishAttemptStats queryStatsByTaskId(@Param("taskId") String taskId);

    List<AiXhsPublishAttempt> listByTaskId(@Param("taskId") String taskId);

    void insert(AiXhsPublishAttempt po);

    void update(AiXhsPublishAttempt po);
}
