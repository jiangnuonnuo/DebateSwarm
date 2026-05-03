package com.dasi.domain.xhspublish.service.impl;

import com.dasi.domain.xhspublish.model.aggregate.XhsPublishTaskAggregate;
import com.dasi.domain.xhspublish.model.entity.*;
import com.dasi.domain.xhspublish.service.IXhsPublishService;
import com.dasi.domain.xhspublish.service.intelligent.XhsPublishIntelligentSubmitDomainService;
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

    @Resource
    private XhsPublishIntelligentSubmitDomainService intelligentSubmitDomainService;

    @Override
    public String createTask(XhsPublishCreateTaskCommandEntity commandEntity) {
        return taskCommandDomainService.createTask(commandEntity);
    }

    @Override
    public void updateTaskContext(XhsPublishUpdateContextCommandEntity commandEntity) {
        taskCommandDomainService.updateTaskContext(commandEntity);
    }

    @Override
    public String submitTask(XhsPublishSubmitTaskCommandEntity commandEntity) {
        return taskCommandDomainService.submitTask(commandEntity);
    }

    @Override
    public PageResult<XhsPublishTaskEntity> pageTask(XhsPublishTaskPageQueryEntity queryEntity) {
        return taskQueryDomainService.pageTask(queryEntity);
    }

    @Override
    public XhsPublishTaskAggregate detailTask(String taskId) {
        return taskQueryDomainService.detailTask(taskId);
    }

    @Override
    public void reviewTask(XhsPublishReviewCommandEntity commandEntity) {
        taskCommandDomainService.reviewTask(commandEntity);
    }

    @Override
    public void retryTask(XhsPublishRetryCommandEntity commandEntity) {
        taskCommandDomainService.retryTask(commandEntity);
    }

    @Override
    public void saveTemplate(XhsPublishTemplateSaveCommandEntity commandEntity) {
        templateDomainService.saveTemplate(commandEntity);
    }

    @Override
    public PageResult<XhsPublishTemplateEntity> pageTemplate(XhsPublishTemplatePageQueryEntity queryEntity) {
        return templateDomainService.pageTemplate(queryEntity);
    }

    @Override
    public List<XhsPublishContentAssetEntity> uploadMaterial(XhsPublishMaterialUploadCommandEntity commandEntity, List<MultipartFile> fileList) {
        return materialDomainService.uploadMaterial(commandEntity, fileList);
    }

    @Override
    public void uploadKnowledge(XhsPublishKnowledgeUploadCommandEntity commandEntity, List<MultipartFile> fileList) {
        knowledgeDomainService.uploadKnowledge(commandEntity, fileList);
    }

    @Override
    public XhsPublishIntelligentSubmitResultEntity submitIntelligentTask(XhsPublishIntelligentSubmitCommandEntity commandEntity, List<MultipartFile> fileList) {
        return intelligentSubmitDomainService.submit(commandEntity, fileList);
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
