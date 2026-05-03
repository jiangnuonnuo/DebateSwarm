package com.dasi.api;

import com.dasi.api.dto.request.xhspublish.*;
import com.dasi.api.dto.response.xhspublish.*;
import com.dasi.types.result.PageResult;
import com.dasi.types.result.Result;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IXhsPublishApi {

    Result<String> createTask(CreateXhsPublishTaskRequestDTO dto);

    Result<Void> updateTaskContext(UpdateXhsPublishTaskContextRequestDTO dto);

    Result<String> submitTask(SubmitXhsPublishTaskRequestDTO dto);

    Result<PageResult<XhsPublishTaskPageResponseDTO>> pageTask(PageXhsPublishTaskRequestDTO dto);

    Result<XhsPublishTaskDetailResponseDTO> detailTask(String taskId);

    Result<Void> reviewTask(ReviewXhsPublishTaskRequestDTO dto);

    Result<Void> retryTask(RetryXhsPublishTaskRequestDTO dto);

    Result<Void> saveTemplate(SaveXhsPublishTemplateRequestDTO dto);

    Result<PageResult<XhsPublishTemplateResponseDTO>> pageTemplate(PageXhsPublishTemplateRequestDTO dto);

    Result<List<XhsPublishAssetResponseDTO>> uploadMaterial(UploadXhsPublishMaterialRequestDTO dto, List<MultipartFile> fileList);

    Result<Void> uploadKnowledge(UploadXhsPublishKnowledgeRequestDTO dto, List<MultipartFile> fileList);

    Result<IntelligentXhsPublishSubmitResponseDTO> submitIntelligentTask(IntelligentXhsPublishSubmitRequestDTO dto, List<MultipartFile> fileList);

}
