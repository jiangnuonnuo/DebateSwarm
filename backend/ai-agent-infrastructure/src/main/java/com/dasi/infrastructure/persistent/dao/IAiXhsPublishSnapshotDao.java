package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiXhsPublishSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IAiXhsPublishSnapshotDao {
    AiXhsPublishSnapshot queryBySnapshotId(@Param("snapshotId") String snapshotId);

    List<AiXhsPublishSnapshot> listByTaskId(@Param("taskId") String taskId);

    List<AiXhsPublishSnapshot> listExpired(@Param("expireTime") LocalDateTime expireTime);

    void insert(AiXhsPublishSnapshot po);

    void update(AiXhsPublishSnapshot po);
}
