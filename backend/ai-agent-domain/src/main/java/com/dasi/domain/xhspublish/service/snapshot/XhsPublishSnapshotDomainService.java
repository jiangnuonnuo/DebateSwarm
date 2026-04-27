package com.dasi.domain.xhspublish.service.snapshot;

import com.dasi.domain.xhspublish.adapter.port.IXhsSnapshotStoragePort;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.model.entity.XhsPublishSnapshotEntity;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class XhsPublishSnapshotDomainService {

    @Resource
    private IXhsPublishRepository publishRepository;

    @Resource
    private IXhsSnapshotStoragePort snapshotStoragePort;

    public void cleanupExpiredSnapshots() {
        List<XhsPublishSnapshotEntity> expiredList = publishRepository.listExpiredSnapshot(LocalDateTime.now());
        for (XhsPublishSnapshotEntity snapshot : expiredList) {
            if (snapshot == null || "deleted".equalsIgnoreCase(snapshot.getCleanupStatus())) {
                continue;
            }
            try {
                snapshotStoragePort.delete(snapshot.getSnapshotPath());
            } catch (Exception e) {
                log.warn("【小红书发布】删除过期快照文件失败：snapshotId={}, path={}", snapshot.getSnapshotId(), snapshot.getSnapshotPath(), e);
            }
            snapshot.setCleanupStatus("deleted");
            snapshot.setDeletedTime(LocalDateTime.now());
            publishRepository.saveSnapshot(snapshot);
        }
    }

}
