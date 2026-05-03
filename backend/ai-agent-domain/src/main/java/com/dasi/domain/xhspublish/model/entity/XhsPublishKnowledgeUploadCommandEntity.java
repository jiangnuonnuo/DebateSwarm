package com.dasi.domain.xhspublish.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishKnowledgeUploadCommandEntity {
    private String title;
    private String ragTag;
    private String knowledgeScope;
    private String knowledgeType;
    private String sourceType;

    private String sourceRef;

    private String summary;

}

