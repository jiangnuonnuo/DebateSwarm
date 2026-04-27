package com.dasi.domain.xhspublish.service.execution.remote;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishRemoteResult {

    private String status;

    private String resultJson;

    private String errorCode;

    private String errorMessage;

    public boolean isFailed() {
        return "failed".equalsIgnoreCase(status);
    }

    public boolean isPublished() {
        return "published".equalsIgnoreCase(status)
                || "verified".equalsIgnoreCase(status)
                || "succeeded".equalsIgnoreCase(status);
    }

}
