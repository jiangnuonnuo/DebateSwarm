package com.dasi.domain.xhspublish.service.generator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedPublishContext {

    private String title;

    private String content;

    @Builder.Default
    private List<String> tags = List.of();

    private String visibility;

    private Boolean isOriginal;

}
