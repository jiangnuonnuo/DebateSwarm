package com.dasi.api.dto.request.xhspublish;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageXhsPublishTaskRequestDTO {

    private String keyword;

    private String taskStatus;

    private String currentStage;

    @Builder.Default
    private Integer pageNum = 1;

    @Builder.Default
    private Integer pageSize = 10;

}

