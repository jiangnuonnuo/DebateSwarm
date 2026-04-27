package com.dasi.domain.xhspublish.service;

import com.dasi.domain.xhspublish.model.dto.CreateXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.PageXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.PageXhsPublishTemplateDTO;
import com.dasi.domain.xhspublish.model.dto.ReviewXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.RetryXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.SaveXhsPublishTemplateDTO;
import com.dasi.domain.xhspublish.model.dto.SubmitXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.dto.UpdateXhsPublishTaskContextDTO;
import com.dasi.domain.xhspublish.model.dto.UploadXhsPublishKnowledgeDTO;
import com.dasi.domain.xhspublish.model.dto.UploadXhsPublishMaterialDTO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishAssetVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTaskDetailVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTaskPageVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTemplateVO;
import com.dasi.domain.xhspublish.service.knowledge.XhsPublishKnowledgeDomainService;
import com.dasi.domain.xhspublish.service.material.XhsPublishMaterialDomainService;
import com.dasi.domain.xhspublish.service.snapshot.XhsPublishSnapshotDomainService;
import com.dasi.domain.xhspublish.service.task.XhsPublishTaskCommandDomainService;
import com.dasi.domain.xhspublish.service.task.XhsPublishTaskQueryDomainService;
import com.dasi.domain.xhspublish.service.template.XhsPublishTemplateDomainService;
import com.dasi.types.result.PageResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class XhsPublishService implements IXhsPublishService {

    @Resource
    private XhsPublishTaskCommandDomainService taskCommandDomainService;

    @Resource
    private XhsPublishTaskQueryDomainService taskQueryDomainService;

    @Resource
    private XhsPublishTemplateDomainService templateDomainService;

    @Resource
    private XhsPublishMaterialDomainService materialDomainService;

    @Resource
    private XhsPublishKnowledgeDomainService knowledgeDomainService;

    @Resource
    private XhsPublishSnapshotDomainService snapshotDomainService;

    @Override
    public String createTask(CreateXhsPublishTaskDTO dto) {
        return taskCommandDomainService.createTask(dto);
    }

    @Override
    public void updateTaskContext(UpdateXhsPublishTaskContextDTO dto) {
        taskCommandDomainService.updateTaskContext(dto);
    }

    @Override
    public String submitTask(SubmitXhsPublishTaskDTO dto) {
        return taskCommandDomainService.submitTask(dto);
    }

    @Override
    public PageResult<XhsPublishTaskPageVO> pageTask(PageXhsPublishTaskDTO dto) {
        return taskQueryDomainService.pageTask(dto);
    }

    @Override
    public XhsPublishTaskDetailVO detailTask(String taskId) {
        return taskQueryDomainService.detailTask(taskId);
    }

    @Override
    public void reviewTask(ReviewXhsPublishTaskDTO dto) {
        taskCommandDomainService.reviewTask(dto);
    }

    @Override
    public void retryTask(RetryXhsPublishTaskDTO dto) {
        taskCommandDomainService.retryTask(dto);
    }

    @Override
    public void saveTemplate(SaveXhsPublishTemplateDTO dto) {
        templateDomainService.saveTemplate(dto);
    }

    @Override
    public PageResult<XhsPublishTemplateVO> pageTemplate(PageXhsPublishTemplateDTO dto) {
        return templateDomainService.pageTemplate(dto);
    }

    @Override
    public List<XhsPublishAssetVO> uploadMaterial(UploadXhsPublishMaterialDTO dto, List<MultipartFile> fileList) {
        return materialDomainService.uploadMaterial(dto, fileList);
    }

    @Override
    public void uploadKnowledge(UploadXhsPublishKnowledgeDTO dto, List<MultipartFile> fileList) {
        knowledgeDomainService.uploadKnowledge(dto, fileList);
    }

    @Override
    public org.springframework.core.io.Resource accessMaterial(String assetId) {
        return materialDomainService.accessMaterial(assetId);
    }

    @Override
    public void cleanupExpiredAssets() {
        materialDomainService.cleanupExpiredAssets();
    }

    @Override
    public void cleanupExpiredSnapshots() {
        snapshotDomainService.cleanupExpiredSnapshots();
    }

}
