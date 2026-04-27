package com.dasi.api;

import com.dasi.domain.xhspublish.model.dto.*;
import com.dasi.domain.xhspublish.model.vo.XhsPublishAssetVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTaskDetailVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTaskPageVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTemplateVO;
import com.dasi.types.result.PageResult;
import com.dasi.types.result.Result;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IXhsPublishApi {

    Result<String> createTask(CreateXhsPublishTaskDTO dto);

    Result<Void> updateTaskContext(UpdateXhsPublishTaskContextDTO dto);

    Result<String> submitTask(SubmitXhsPublishTaskDTO dto);

    Result<PageResult<XhsPublishTaskPageVO>> pageTask(PageXhsPublishTaskDTO dto);

    Result<XhsPublishTaskDetailVO> detailTask(String taskId);

    Result<Void> reviewTask(ReviewXhsPublishTaskDTO dto);

    Result<Void> retryTask(RetryXhsPublishTaskDTO dto);

    Result<Void> saveTemplate(SaveXhsPublishTemplateDTO dto);

    Result<PageResult<XhsPublishTemplateVO>> pageTemplate(PageXhsPublishTemplateDTO dto);

    Result<List<XhsPublishAssetVO>> uploadMaterial(UploadXhsPublishMaterialDTO dto, List<MultipartFile> fileList);

    Result<Void> uploadKnowledge(UploadXhsPublishKnowledgeDTO dto, List<MultipartFile> fileList);

}
