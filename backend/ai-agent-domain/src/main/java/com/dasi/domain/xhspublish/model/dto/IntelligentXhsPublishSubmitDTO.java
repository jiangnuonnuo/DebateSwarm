package com.dasi.domain.xhspublish.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntelligentXhsPublishSubmitDTO {

    @NotBlank
    private String taskName;

    @NotBlank
    private String clientId;

    @NotBlank
    private String publishRequirement;

    private String bindingId;

    private String visibility;

    private Boolean isOriginal;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime scheduledPublishAt;

    private List<String> originImageUrls;

}
