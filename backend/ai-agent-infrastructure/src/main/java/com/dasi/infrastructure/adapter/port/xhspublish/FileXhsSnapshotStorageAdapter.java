package com.dasi.infrastructure.adapter.port.xhspublish;

import com.dasi.domain.xhspublish.config.XhsPublishProperties;
import com.dasi.domain.xhspublish.adapter.port.IXhsSnapshotStoragePort;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

@Slf4j
@Service
public class FileXhsSnapshotStorageAdapter implements IXhsSnapshotStoragePort {

    @Resource
    private XhsPublishProperties xhsPublishProperties;

    @Override
    public String save(String taskId, Integer roundNo, String stage, String content) {
        try {
            Path basePath = Paths.get(xhsPublishProperties.getSnapshotBaseDir()).toAbsolutePath().normalize();
            Path targetDir = basePath.resolve(LocalDate.now().toString()).resolve(safePath(taskId));
            Files.createDirectories(targetDir);
            String fileName = String.format("round_%s_%s.json", roundNo == null ? 0 : roundNo, safePath(stage));
            Path targetFile = targetDir.resolve(fileName).normalize();
            Files.writeString(targetFile, StringUtils.hasText(content) ? content : "{}", StandardCharsets.UTF_8);
            return targetFile.toAbsolutePath().toString();
        } catch (IOException e) {
            log.error("【小红书发布】快照保存失败：taskId={}, roundNo={}, stage={}", taskId, roundNo, stage, e);
            throw new WorkException("快照保存失败");
        }
    }

    @Override
    public void delete(String snapshotPath) {
        if (!StringUtils.hasText(snapshotPath)) {
            return;
        }
        try {
            Path path = resolvePathWithinBase(snapshotPath, false);
            if (path == null) {
                log.warn("【小红书发布】快照删除被拒绝，路径越界：snapshotPath={}", snapshotPath);
                return;
            }
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("【小红书发布】快照删除失败：snapshotPath={}", snapshotPath, e);
        }
    }

    private String safePath(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "unknown";
        }
        return raw.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private Path resolvePathWithinBase(String snapshotPath, boolean throwOnOutOfBase) {
        try {
            Path basePath = Paths.get(xhsPublishProperties.getSnapshotBaseDir()).toAbsolutePath().normalize();
            Files.createDirectories(basePath);
            Path targetPath = Paths.get(snapshotPath).toAbsolutePath().normalize();
            if (!targetPath.startsWith(basePath)) {
                if (throwOnOutOfBase) {
                    throw new WorkException("快照路径非法");
                }
                return null;
            }
            return targetPath;
        } catch (IOException e) {
            throw new WorkException("快照路径解析失败");
        }
    }

}

