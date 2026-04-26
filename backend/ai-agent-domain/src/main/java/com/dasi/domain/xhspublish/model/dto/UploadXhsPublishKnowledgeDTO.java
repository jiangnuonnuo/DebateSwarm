package com.dasi.domain.xhspublish.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadXhsPublishKnowledgeDTO {

    @NotBlank
    private String title;

    @NotBlank
    private String ragTag;

    @NotBlank
    private String knowledgeScope;

    @NotBlank
    private String knowledgeType;

    @NotBlank
    private String sourceType;

    private String sourceRef;

    private String summary;

}
