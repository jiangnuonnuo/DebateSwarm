package com.dasi.domain.xhspublish.service;

import com.dasi.domain.xhspublish.model.dto.*;
import com.dasi.domain.xhspublish.model.vo.IntelligentXhsPublishSubmitVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishAssetVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTaskDetailVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTaskPageVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTemplateVO;
import com.dasi.types.result.PageResult;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IXhsPublishService {

    String createTask(CreateXhsPublishTaskDTO dto);

    void updateTaskContext(UpdateXhsPublishTaskContextDTO dto);

    String submitTask(SubmitXhsPublishTaskDTO dto);

    PageResult<XhsPublishTaskPageVO> pageTask(PageXhsPublishTaskDTO dto);

    XhsPublishTaskDetailVO detailTask(String taskId);

    void reviewTask(ReviewXhsPublishTaskDTO dto);

    void retryTask(RetryXhsPublishTaskDTO dto);

    void saveTemplate(SaveXhsPublishTemplateDTO dto);

    PageResult<XhsPublishTemplateVO> pageTemplate(PageXhsPublishTemplateDTO dto);

    List<XhsPublishAssetVO> uploadMaterial(UploadXhsPublishMaterialDTO dto, List<MultipartFile> fileList);

    void uploadKnowledge(UploadXhsPublishKnowledgeDTO dto, List<MultipartFile> fileList);

    IntelligentXhsPublishSubmitVO submitIntelligentTask(IntelligentXhsPublishSubmitDTO dto, List<MultipartFile> fileList);

    Resource accessMaterial(String assetId);

    void cleanupExpiredAssets();

    void cleanupExpiredSnapshots();

}
