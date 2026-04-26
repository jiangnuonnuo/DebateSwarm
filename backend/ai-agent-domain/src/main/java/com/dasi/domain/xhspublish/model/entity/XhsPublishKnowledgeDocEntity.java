package com.dasi.domain.xhspublish.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/** 小红书知识主档领域实体 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XhsPublishKnowledgeDocEntity {

    private Long id;

    private String knowledgeId;

    private Long userId;

    private String knowledgeScope;

    private String knowledgeType;

    private String title;

    private String ragTag;

    private String sourceType;

    private String sourceRef;

    private String summary;

    private String docStatus;

    private String vectorStatus;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
