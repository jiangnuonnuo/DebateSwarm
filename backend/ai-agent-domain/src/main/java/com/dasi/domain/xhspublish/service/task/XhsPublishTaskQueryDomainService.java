package com.dasi.domain.xhspublish.service.task;

import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.model.aggregate.XhsPublishTaskAggregate;
import com.dasi.domain.xhspublish.model.dto.PageXhsPublishTaskDTO;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.model.vo.XhsPublishAssetVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishAttemptVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishReviewVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTaskDetailVO;
import com.dasi.domain.xhspublish.model.vo.XhsPublishTaskPageVO;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskAccessSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishViewAssembler;
import com.dasi.types.result.PageResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class XhsPublishTaskQueryDomainService {

    @Resource
    private IXhsPublishRepository publishRepository;

    @Resource
    private XhsPublishTaskAccessSupport taskAccessSupport;

    @Resource
    private XhsPublishViewAssembler viewAssembler;

    public PageResult<XhsPublishTaskPageVO> pageTask(PageXhsPublishTaskDTO dto) {
        Long userId = taskAccessSupport.requireUserId();
        int pageNum = Math.max(dto.getPageNum(), 1);
        int pageSize = Math.max(dto.getPageSize(), 1);
        int offset = (pageNum - 1) * pageSize;
        List<XhsPublishTaskPageVO> list = publishRepository.pageTask(userId, dto.getKeyword(), dto.getTaskStatus(), dto.getCurrentStage(), offset, pageSize).stream()
                .map(viewAssembler::toTaskPageVO)
                .toList();
        Integer total = publishRepository.countTask(userId, dto.getKeyword(), dto.getTaskStatus(), dto.getCurrentStage());
        int pageSum = (total + pageSize - 1) / pageSize;
        return PageResult.<XhsPublishTaskPageVO>builder()
                .list(list)
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .pageSum(pageSum)
                .build();
    }

    public XhsPublishTaskDetailVO detailTask(String taskId) {
        XhsPublishTaskAggregate aggregate = taskAccessSupport.queryOwnedAggregate(taskId);
        XhsPublishTaskEntity task = aggregate.getTask();
        List<XhsPublishAttemptVO> attemptList = aggregate.getAttemptList().stream().map(viewAssembler::toAttemptVO).toList();
        List<XhsPublishReviewVO> reviewList = aggregate.getReviewList().stream().map(viewAssembler::toReviewVO).toList();
        List<XhsPublishAssetVO> assetList = aggregate.getAssetList().stream().map(viewAssembler::toAssetVO).toList();
        return XhsPublishTaskDetailVO.builder()
                .taskId(task.getTaskId())
                .taskName(task.getTaskName())
                .publishMode(task.getPublishMode())
                .publishType(task.getPublishType())
                .taskStatus(task.getTaskStatus())
                .currentStage(task.getCurrentStage())
                .bindingId(task.getBindingId())
                .templateId(task.getTemplateId())
                .scheduledPublishAt(task.getScheduledPublishAt())
                .requestJson(task.getRequestJson())
                .latestContextJson(task.getLatestContextJson())
                .finalResultJson(task.getFinalResultJson())
                .retryPolicyJson(task.getRetryPolicyJson())
                .createTime(task.getCreateTime())
                .updateTime(task.getUpdateTime())
                .attemptList(attemptList)
                .reviewList(reviewList)
                .assetList(assetList)
                .build();
    }

}
