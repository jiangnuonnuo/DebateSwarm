package com.dasi.domain.xhspublish.service.execution.snapshot;

import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishConfigRepository;
import com.dasi.domain.xhspublish.model.entity.XhsPublishAttemptEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishSnapshotEntity;
import com.dasi.domain.xhspublish.adapter.port.IXhsSnapshotStoragePort;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.service.context.XhsPublishExecutionContext;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class XhsPublishSnapshotManager implements IXhsPublishSnapshotManager {

    private static final int FAILURE_ROUND_OFFSET = 1000;
    private static final int RESULT_ROUND_OFFSET = 2000;

    @Resource
    private IXhsSnapshotStoragePort snapshotStoragePort;

    @Resource
    private IXhsPublishRepository publishRepository;

    @Resource
    private XhsPublishIdSupport idSupport;

    @Resource
    private IXhsPublishConfigRepository xhsPublishConfigRepository;

    @Override
    public XhsPublishSnapshotEntity savePayloadSnapshot(XhsPublishExecutionContext executionContext, String publishRequestJson) {
        return save(executionContext, safeAttemptNo(executionContext.getAttempt()), executionContext.getTask().getCurrentStage(), publishRequestJson);
    }

    @Override
    public XhsPublishSnapshotEntity saveFailureSnapshot(XhsPublishExecutionContext executionContext, String failureResultJson) {
        return save(executionContext, safeAttemptNo(executionContext.getAttempt()) + FAILURE_ROUND_OFFSET, "publish_failed", failureResultJson);
    }

    @Override
    public XhsPublishSnapshotEntity saveResultSnapshot(XhsPublishExecutionContext executionContext, String resultJson) {
        return save(executionContext, safeAttemptNo(executionContext.getAttempt()) + RESULT_ROUND_OFFSET, "publish_result", resultJson);
    }

    @Override
    public void keep(XhsPublishSnapshotEntity snapshotEntity) {
        if (snapshotEntity == null) {
            return;
        }
        snapshotEntity.setCleanupStatus("pending");
        publishRepository.saveSnapshot(snapshotEntity);
    }

    @Override
    public void delete(XhsPublishSnapshotEntity snapshotEntity) {
        if (snapshotEntity == null) {
            return;
        }
        snapshotStoragePort.delete(snapshotEntity.getSnapshotPath());
        snapshotEntity.setCleanupStatus("deleted");
        snapshotEntity.setDeletedTime(LocalDateTime.now());
        publishRepository.saveSnapshot(snapshotEntity);
    }

    private XhsPublishSnapshotEntity save(XhsPublishExecutionContext executionContext, Integer roundNo, String stage, String content) {
        String snapshotPath = snapshotStoragePort.save(executionContext.getTask().getTaskId(), roundNo, stage, content);
        XhsPublishSnapshotEntity entity = XhsPublishSnapshotEntity.builder()
                .snapshotId(idSupport.nextSnapshotId())
                .taskId(executionContext.getTask().getTaskId())
                .attemptId(executionContext.getAttempt().getAttemptId())
                .roundNo(roundNo)
                .stage(stage)
                .snapshotPath(snapshotPath)
                .cleanupStatus("pending")
                .expireTime(LocalDateTime.now().plusHours(defaultHours(xhsPublishConfigRepository.getSnapshotRetentionHours())))
                .build();
        publishRepository.saveSnapshot(entity);
        return publishRepository.querySnapshotBySnapshotId(entity.getSnapshotId());
    }

    private Integer safeAttemptNo(XhsPublishAttemptEntity attempt) {
        return attempt == null || attempt.getAttemptNo() == null ? 1 : attempt.getAttemptNo();
    }

    private int defaultHours(Integer hours) {
        return hours == null ? 24 : hours;
    }

}

