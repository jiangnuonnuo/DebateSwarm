package com.dasi.trigger.controller;

import com.dasi.api.IXhsPublishApi;
import com.dasi.domain.xhspublish.model.dto.*;
import com.dasi.domain.xhspublish.model.vo.XhsPublishAssetVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTaskDetailVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTaskPageVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTemplateVO;
import com.dasi.domain.xhspublish.service.IXhsPublishService;
import com.dasi.types.result.PageResult;
import com.dasi.types.result.Result;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/xhs/publish")
public class XhsPublishController implements IXhsPublishApi {

    @Resource
    private IXhsPublishService xhsPublishService;

    @Override
    @PostMapping("/task/create")
    public Result<String> createTask(@Valid @RequestBody CreateXhsPublishTaskDTO dto) {
        log.info("【小红书发布】接收创建任务请求：{}", dto);
        return Result.success(xhsPublishService.createTask(dto));
    }

    @Override
    @PostMapping("/task/page")
    public Result<PageResult<XhsPublishTaskPageVO>> pageTask(@Valid @RequestBody PageXhsPublishTaskDTO dto) {
        log.info("【小红书发布】接收任务分页请求：{}", dto);
        return Result.success(xhsPublishService.pageTask(dto));
    }

    @Override
    @PostMapping("/task/detail")
    public Result<XhsPublishTaskDetailVO> detailTask(@NotBlank @RequestParam String taskId) {
        log.info("【小红书发布】接收任务详情请求：taskId={}", taskId);
        return Result.success(xhsPublishService.detailTask(taskId));
    }

    @Override
    @PostMapping("/task/review")
    public Result<Void> reviewTask(@Valid @RequestBody ReviewXhsPublishTaskDTO dto) {
        log.info("【小红书发布】接收审核请求：{}", dto);
        xhsPublishService.reviewTask(dto);
        return Result.success();
    }

    @Override
    @PostMapping("/task/retry")
    public Result<Void> retryTask(@Valid @RequestBody RetryXhsPublishTaskDTO dto) {
        log.info("【小红书发布】接收重试请求：{}", dto);
        xhsPublishService.retryTask(dto);
        return Result.success();
    }

    @Override
    @PostMapping("/template/save")
    public Result<Void> saveTemplate(@Valid @RequestBody SaveXhsPublishTemplateDTO dto) {
        log.info("【小红书发布】接收模板保存请求：{}", dto);
        xhsPublishService.saveTemplate(dto);
        return Result.success();
    }

    @Override
    @PostMapping("/template/page")
    public Result<PageResult<XhsPublishTemplateVO>> pageTemplate(@Valid @RequestBody PageXhsPublishTemplateDTO dto) {
        log.info("【小红书发布】接收模板分页请求：{}", dto);
        return Result.success(xhsPublishService.pageTemplate(dto));
    }

    @Override
    @PostMapping("/material/upload")
    public Result<List<XhsPublishAssetVO>> uploadMaterial(@Valid @ModelAttribute UploadXhsPublishMaterialDTO dto,
                                                          @RequestPart(value = "fileList", required = false) List<MultipartFile> fileList) {
        log.info("【小红书发布】接收素材上传请求：dto={}, fileCount={}", dto, fileList == null ? 0 : fileList.size());
        return Result.success(xhsPublishService.uploadMaterial(dto, fileList));
    }

    @Override
    @PostMapping("/knowledge/upload")
    public Result<Void> uploadKnowledge(@Valid @ModelAttribute UploadXhsPublishKnowledgeDTO dto,
                                        @RequestPart(value = "fileList", required = false) List<MultipartFile> fileList) {
        log.info("【小红书发布】接收知识上传请求：dto={}, fileCount={}", dto, fileList == null ? 0 : fileList.size());
        xhsPublishService.uploadKnowledge(dto, fileList);
        return Result.success();
    }

    @GetMapping("/material/access")
    public ResponseEntity<org.springframework.core.io.Resource> accessMaterial(@RequestParam String assetId) {
        org.springframework.core.io.Resource resource = xhsPublishService.accessMaterial(assetId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

}
