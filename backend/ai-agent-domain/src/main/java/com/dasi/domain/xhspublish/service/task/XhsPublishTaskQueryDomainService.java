package com.dasi.domain.xhspublish.service.task;

import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.model.aggregate.XhsPublishTaskAggregate;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskPageQueryEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskAccessSupport;
import com.dasi.types.result.PageResult;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class XhsPublishTaskQueryDomainService {

    @Resource
    private IXhsPublishRepository publishRepository;

    @Resource
    private XhsPublishTaskAccessSupport taskAccessSupport;

    public PageResult<XhsPublishTaskEntity> pageTask(XhsPublishTaskPageQueryEntity dto) {
        Long userId = taskAccessSupport.requireUserId();
        int pageNum = Math.max(dto.getPageNum(), 1);
        int pageSize = Math.max(dto.getPageSize(), 1);
        int offset = (pageNum - 1) * pageSize;
        var list = publishRepository.pageTask(userId, dto.getKeyword(), dto.getTaskStatus(), dto.getCurrentStage(), offset, pageSize);
        Integer total = publishRepository.countTask(userId, dto.getKeyword(), dto.getTaskStatus(), dto.getCurrentStage());
        int pageSum = (total + pageSize - 1) / pageSize;
        return PageResult.<XhsPublishTaskEntity>builder()
                .list(list)
                .total(total)
                .pageNum(pageNum)
                .pageSize(pageSize)
                .pageSum(pageSum)
                .build();
    }

    public XhsPublishTaskAggregate detailTask(String taskId) {
        return taskAccessSupport.queryOwnedAggregate(taskId);
    }

}
