package com.dasi.domain.xhspublish.service.knowledge;

import com.dasi.domain.xhspublish.adapter.port.IXhsVectorStorePort;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishKnowledgeDocRepository;
import com.dasi.domain.xhspublish.model.entity.XhsPublishKnowledgeUploadCommandEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishKnowledgeDocEntity;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskAccessSupport;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class XhsPublishKnowledgeDomainService {

    @Resource
    private IXhsPublishKnowledgeDocRepository knowledgeDocRepository;

    @Resource
    private IXhsVectorStorePort vectorStorePort;

    @Resource
    private XhsPublishTaskAccessSupport taskAccessSupport;

    @Resource
    private XhsPublishIdSupport idSupport;

    @Resource
    private TokenTextSplitter tokenTextSplitter;

    @Transactional(rollbackFor = Exception.class)
    public void uploadKnowledge(XhsPublishKnowledgeUploadCommandEntity dto, List<MultipartFile> fileList) {
        Long userId = taskAccessSupport.requireUserId();
        if ("shared".equalsIgnoreCase(dto.getKnowledgeScope()) && !"admin".equalsIgnoreCase(taskAccessSupport.requireUserRole())) {
            throw new WorkException("共享知识库仅管理员可写");
        }

        String knowledgeId = idSupport.nextKnowledgeId();
        XhsPublishKnowledgeDocEntity entity = XhsPublishKnowledgeDocEntity.builder()
                .knowledgeId(knowledgeId)
                .userId(userId)
                .knowledgeScope(dto.getKnowledgeScope())
                .knowledgeType(dto.getKnowledgeType())
                .title(dto.getTitle())
                .ragTag(dto.getRagTag())
                .sourceType(dto.getSourceType())
                .sourceRef(dto.getSourceRef())
                .summary(dto.getSummary())
                .docStatus("active")
                .vectorStatus("pending")
                .build();
        knowledgeDocRepository.insert(entity);
        entity = knowledgeDocRepository.queryByKnowledgeId(knowledgeId);

        try {
            int chunks = 0;
            if (fileList != null && !fileList.isEmpty()) {
                for (MultipartFile file : fileList) {
                    TikaDocumentReader reader = new TikaDocumentReader(file.getResource());
                    List<Document> documents = tokenTextSplitter.apply(reader.get());
                    for (Document document : documents) {
                        if (document == null || !StringUtils.hasText(document.getText())) {
                            continue;
                        }
                        vectorStorePort.upsert(document.getText(), buildKnowledgeMetadata(dto, knowledgeId, userId));
                        chunks++;
                    }
                }
            } else if (StringUtils.hasText(dto.getSummary()) || StringUtils.hasText(dto.getSourceRef())) {
                String content = StringUtils.hasText(dto.getSummary()) ? dto.getSummary() : dto.getSourceRef();
                vectorStorePort.upsert(content, buildKnowledgeMetadata(dto, knowledgeId, userId));
                chunks++;
            } else {
                throw new WorkException("知识内容不能为空，请上传文件或填写摘要/来源文本");
            }

            entity.setVectorStatus(chunks > 0 ? "success" : "failed");
            knowledgeDocRepository.update(entity);
        } catch (Exception e) {
            entity.setVectorStatus("failed");
            knowledgeDocRepository.update(entity);
            throw e;
        }
    }

    private Map<String, Object> buildKnowledgeMetadata(XhsPublishKnowledgeUploadCommandEntity dto, String knowledgeId, Long userId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("bizDomain", "xhs_publish");
        metadata.put("knowledgeId", knowledgeId);
        metadata.put("knowledge", dto.getRagTag());
        metadata.put("knowledgeScope", dto.getKnowledgeScope());
        metadata.put("knowledgeType", dto.getKnowledgeType());
        metadata.put("userId", String.valueOf(userId));
        metadata.put("sourceType", dto.getSourceType());
        metadata.put("status", "active");
        metadata.put("docTitle", dto.getTitle());
        return metadata;
    }

}
