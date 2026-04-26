package com.dasi.domain.xhspublish.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "miniagent.xhs-publish", ignoreInvalidFields = true)
public class XhsPublishProperties {

    private String assetBaseDir;

    private String assetAccessBaseUrl;

    private String snapshotBaseDir;

    private Integer assetRetentionHours;

    private Integer snapshotRetentionHours;

    private String defaultTenantId;

    private String defaultAccountId;

    private String defaultAccountName;

    private String vectorSchemaName;

    private String vectorTableName;

    private String assetCleanupCron;

    private String snapshotCleanupCron;

    private String mcpBaseUrl;

    private String mcpEndpoint;

    private Integer mcpTimeoutMinutes;

    private Integer mcpPollRounds;

    private Integer mcpPollIntervalMillis;

}
