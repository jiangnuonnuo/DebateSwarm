package com.dasi.domain.xhspublish.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishAssetVO {

    private String assetId;

    private String assetType;

    private String sourceType;

    private String storageType;

    private String accessUrl;

    private Integer sortNo;

    private String assetStatus;

    private LocalDateTime expireTime;

}
