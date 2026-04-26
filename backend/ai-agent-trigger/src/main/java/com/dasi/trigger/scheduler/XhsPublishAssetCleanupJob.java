package com.dasi.trigger.scheduler;

import com.dasi.domain.xhspublish.service.IXhsPublishService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class XhsPublishAssetCleanupJob {

    @Resource
    private IXhsPublishService xhsPublishService;

    @Scheduled(cron = "${miniagent.xhs-publish.asset-cleanup-cron:0 0 */1 * * ?}")
    public void cleanupExpiredAssets() {
        log.info("【小红书发布】开始清理过期素材");
        xhsPublishService.cleanupExpiredAssets();
    }

}
