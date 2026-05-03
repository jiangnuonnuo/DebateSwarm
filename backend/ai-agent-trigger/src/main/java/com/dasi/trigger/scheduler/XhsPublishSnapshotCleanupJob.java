package com.dasi.trigger.scheduler;

import com.dasi.domain.xhspublish.service.snapshot.XhsPublishSnapshotDomainService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class XhsPublishSnapshotCleanupJob {

    @Resource
    private XhsPublishSnapshotDomainService snapshotDomainService;

    @Scheduled(cron = "${miniagent.xhs-publish.snapshot-cleanup-cron:0 30 */1 * * ?}")
    public void cleanupExpiredSnapshots() {
        log.info("【小红书发布】开始清理过期快照");
        snapshotDomainService.cleanupExpiredSnapshots();
    }

}
