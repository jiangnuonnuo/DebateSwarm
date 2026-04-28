package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiXhsPublishTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface IAiXhsPublishTaskDao {
    AiXhsPublishTask queryByTaskId(@Param("taskId") String taskId);

    List<AiXhsPublishTask> page(@Param("userId") Long userId,
                    @Param("keyword") String keyword,
                    @Param("taskStatus") String taskStatus,
                    @Param("currentStage") String currentStage,
                    @Param("offset") Integer offset,
                    @Param("size") Integer size);

    Integer count(@Param("userId") Long userId,
                  @Param("keyword") String keyword,
                  @Param("taskStatus") String taskStatus,
                  @Param("currentStage") String currentStage);

    void insert(AiXhsPublishTask po);

    void update(AiXhsPublishTask po);
}
