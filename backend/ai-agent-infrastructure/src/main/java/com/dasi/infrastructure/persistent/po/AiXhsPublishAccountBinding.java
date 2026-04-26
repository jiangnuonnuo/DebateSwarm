package com.dasi.infrastructure.persistent.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** 小红书账号绑定持久化对象 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiXhsPublishAccountBinding {

    private Long id;

    private String bindingId;

    private Long userId;

    private String accountName;

    private String mcpTenantId;

    private String mcpAccountId;

    private Integer bindStatus;

    private Integer isDefault;

    private LocalDateTime lastCheckTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
