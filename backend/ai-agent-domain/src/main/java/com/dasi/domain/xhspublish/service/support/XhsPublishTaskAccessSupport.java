package com.dasi.domain.xhspublish.service.support;

import com.dasi.domain.util.jwt.UserContext;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.model.aggregate.XhsPublishTaskAggregate;
import com.dasi.domain.xhspublish.model.entity.XhsPublishTaskEntity;
import com.dasi.domain.xhspublish.model.valobj.PublishModeVO;
import com.dasi.domain.xhspublish.model.valobj.PublishTypeVO;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class XhsPublishTaskAccessSupport {

    @Resource
    private IXhsPublishRepository publishRepository;

    @Resource
    private UserContext userContext;

    public Long requireUserId() {
        Long userId = userContext.getUserId();
        if (userId == null) {
            throw new WorkException("当前用户未登录");
        }
        return userId;
    }

    public String requireUserRole() {
        return userContext.getUserRole();
    }

    public XhsPublishTaskEntity queryOwnedTask(String taskId) {
        XhsPublishTaskEntity task = publishRepository.queryTaskByTaskId(taskId);
        Long userId = requireUserId();
        if (task == null || !Objects.equals(task.getUserId(), userId)) {
            throw new WorkException("发布任务不存在");
        }
        return task;
    }

    public XhsPublishTaskAggregate queryOwnedAggregate(String taskId) {
        XhsPublishTaskAggregate aggregate = publishRepository.queryAggregateByTaskId(taskId);
        Long userId = requireUserId();
        if (aggregate == null || aggregate.getTask() == null || !Objects.equals(aggregate.getTask().getUserId(), userId)) {
            throw new WorkException("发布任务不存在");
        }
        return aggregate;
    }

    public void validateModeAndType(String publishMode, String publishType) {
        try {
            PublishModeVO.valueOf(publishMode);
        } catch (Exception e) {
            throw new WorkException("当前仅支持 A/B 两种发布模式");
        }
        try {
            PublishTypeVO.valueOf(publishType);
        } catch (Exception e) {
            throw new WorkException("当前仅支持 image/video 发布类型");
        }
    }

}
