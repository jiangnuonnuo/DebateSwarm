package com.dasi.api;

import com.dasi.api.dto.request.xhspublish.*;
import com.dasi.api.dto.response.xhspublish.*;
import com.dasi.types.result.PageResult;
import com.dasi.types.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IXhsPublishApi {

    Result<String> createTask(@Valid CreateXhsPublishTaskRequestDTO dto);

    Result<Void> updateTaskContext(@Valid UpdateXhsPublishTaskContextRequestDTO dto);

    Result<String> submitTask(@Valid SubmitXhsPublishTaskRequestDTO dto);

    Result<PageResult<XhsPublishTaskPageResponseDTO>> pageTask(@Valid PageXhsPublishTaskRequestDTO dto);

    Result<XhsPublishTaskDetailResponseDTO> detailTask(@NotBlank String taskId);

    Result<Void> reviewTask(@Valid ReviewXhsPublishTaskRequestDTO dto);

    Result<Void> retryTask(@Valid RetryXhsPublishTaskRequestDTO dto);

    Result<Void> saveTemplate(@Valid SaveXhsPublishTemplateRequestDTO dto);

    Result<PageResult<XhsPublishTemplateResponseDTO>> pageTemplate(@Valid PageXhsPublishTemplateRequestDTO dto);

    Result<List<XhsPublishAssetResponseDTO>> uploadMaterial(@Valid UploadXhsPublishMaterialRequestDTO dto, List<MultipartFile> fileList);

    Result<Void> uploadKnowledge(@Valid UploadXhsPublishKnowledgeRequestDTO dto, List<MultipartFile> fileList);

    Result<IntelligentXhsPublishSubmitResponseDTO> submitIntelligentTask(@Valid IntelligentXhsPublishSubmitRequestDTO dto, List<MultipartFile> fileList);

}
