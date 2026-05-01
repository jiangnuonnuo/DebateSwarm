package com.dasi.domain.xhspublish.adapter.repository;

public interface IXhsPublishConfigRepository {

    String getAssetBaseDir();

    String getAssetAccessBaseUrl();

    String getSnapshotBaseDir();

    Integer getAssetRetentionHours();

    Integer getSnapshotRetentionHours();

    String getDefaultTenantId();

    String getDefaultAccountId();

    String getDefaultAccountName();

    String getVectorSchemaName();

    String getVectorTableName();

    Integer getMcpPollRounds();

    Integer getMcpPollIntervalMillis();

}
