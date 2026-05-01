package com.dasi.domain.xhspublish.service.intelligent.guard;

import com.dasi.domain.xhspublish.model.dto.IntelligentXhsPublishSubmitDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntelligentSubmitGuardContext {

    private IntelligentXhsPublishSubmitDTO request;

    @Builder.Default
    private List<MultipartFile> fileList = List.of();

    @Builder.Default
    private List<String> originImageUrls = List.of();

}
