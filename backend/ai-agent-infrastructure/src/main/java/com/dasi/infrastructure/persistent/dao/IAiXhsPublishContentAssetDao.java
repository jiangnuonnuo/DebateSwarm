package com.dasi.infrastructure.persistent.dao;

import com.dasi.infrastructure.persistent.po.AiXhsPublishContentAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface IAiXhsPublishContentAssetDao {
    AiXhsPublishContentAsset queryByAssetId(@Param("assetId") String assetId);

    List<AiXhsPublishContentAsset> listByTaskId(@Param("taskId") String taskId);

    List<AiXhsPublishContentAsset> listByAttemptId(@Param("attemptId") String attemptId);

    List<AiXhsPublishContentAsset> listExpired(@Param("expireTime") LocalDateTime expireTime);

    void insert(AiXhsPublishContentAsset po);

    void update(AiXhsPublishContentAsset po);
}
