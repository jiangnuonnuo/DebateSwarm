package com.dasi.infrastructure.dao.po.xhspublish;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** 小红书发布素材持久化对象 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiXhsPublishContentAsset {

    private Long id;

    private String assetId;

    private String taskId;

    private String attemptId;

    private Long userId;

    private String assetType;

    private String sourceType;

    private String originUrl;

    private String storageType;

    private String storageRef;

    private String accessUrl;

    private Integer sortNo;

    private String assetStatus;

    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

