package com.dasi.domain.xhspublish.service;

import com.dasi.domain.xhspublish.model.aggregate.XhsPublishTaskAggregate;
import com.dasi.domain.xhspublish.model.entity.*;
import com.dasi.types.result.PageResult;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IXhsPublishService {

    String createTask(XhsPublishCreateTaskCommandEntity commandEntity);

    void updateTaskContext(XhsPublishUpdateContextCommandEntity commandEntity);

    String submitTask(XhsPublishSubmitTaskCommandEntity commandEntity);

    PageResult<XhsPublishTaskEntity> pageTask(XhsPublishTaskPageQueryEntity queryEntity);

    XhsPublishTaskAggregate detailTask(String taskId);

    void reviewTask(XhsPublishReviewCommandEntity commandEntity);

    void retryTask(XhsPublishRetryCommandEntity commandEntity);

    void saveTemplate(XhsPublishTemplateSaveCommandEntity commandEntity);

    PageResult<XhsPublishTemplateEntity> pageTemplate(XhsPublishTemplatePageQueryEntity queryEntity);

    List<XhsPublishContentAssetEntity> uploadMaterial(XhsPublishMaterialUploadCommandEntity commandEntity, List<MultipartFile> fileList);

    void uploadKnowledge(XhsPublishKnowledgeUploadCommandEntity commandEntity, List<MultipartFile> fileList);

    XhsPublishIntelligentSubmitResultEntity submitIntelligentTask(XhsPublishIntelligentSubmitCommandEntity commandEntity, List<MultipartFile> fileList);

    Resource accessMaterial(String assetId);

    void cleanupExpiredAssets();

    void cleanupExpiredSnapshots();

}
