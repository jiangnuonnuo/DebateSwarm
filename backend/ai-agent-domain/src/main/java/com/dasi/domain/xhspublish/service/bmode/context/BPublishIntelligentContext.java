package com.dasi.domain.xhspublish.service.bmode.context;

import com.dasi.domain.xhspublish.model.entity.XhsPublishIntelligentSubmitCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishIntelligentSubmitResultEntity;
import com.dasi.domain.xhspublish.service.generator.GeneratedPublishContext;
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
public class BPublishIntelligentContext {

    private XhsPublishIntelligentSubmitCommandEntity command;

    private List<MultipartFile> fileList;

    private List<MultipartFile> normalizedFiles;

    private List<String> normalizedOriginImageUrls;

    private String taskId;

    private List<String> selectedAssetIds;

    private GeneratedPublishContext generatedPublishContext;

    private String latestContextJson;

    private String attemptId;

    public XhsPublishIntelligentSubmitResultEntity toResult() {
        return XhsPublishIntelligentSubmitResultEntity.builder()
                .taskId(taskId)
                .attemptId(attemptId)
                .build();
    }

}
