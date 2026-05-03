package com.dasi.domain.xhspublish.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishIntelligentSubmitCommandEntity {
    private String taskName;
    private String clientId;
    private String publishRequirement;

    private String bindingId;

    private String visibility;

    private Boolean isOriginal;
    private LocalDateTime scheduledPublishAt;

    private List<String> originImageUrls;

}

