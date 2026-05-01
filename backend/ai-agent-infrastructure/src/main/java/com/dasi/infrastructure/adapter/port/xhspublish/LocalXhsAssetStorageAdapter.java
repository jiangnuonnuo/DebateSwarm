package com.dasi.infrastructure.adapter.port.xhspublish;

import com.dasi.domain.xhspublish.adapter.port.IXhsAssetStoragePort;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishConfigRepository;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
public class LocalXhsAssetStorageAdapter implements IXhsAssetStoragePort {

    @Resource
    private IXhsPublishConfigRepository xhsPublishConfigRepository;

    @Override
    public String save(MultipartFile file, String businessPath) {
        if (file == null || file.isEmpty()) {
            throw new WorkException("素材文件不能为空");
        }
        try {
            String fileName = file.getOriginalFilename();
            String extension = resolveExtension(fileName);
            Path basePath = resolveBasePath();
            Path targetDir = basePath.resolve(normalizeBusinessPath(businessPath));
            Files.createDirectories(targetDir);

            String targetName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().replace("-", "") + extension;
            Path targetPath = targetDir.resolve(targetName).normalize();
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return targetPath.toAbsolutePath().toString();
        } catch (IOException e) {
            log.error("【小红书发布】本地素材保存失败：businessPath={}", businessPath, e);
            throw new WorkException("素材保存失败");
        }
    }

    @Override
    public void delete(String storageRef) {
        if (!StringUtils.hasText(storageRef)) {
            return;
        }
        try {
            Path path = resolvePathWithinBase(storageRef, false);
            if (path == null) {
                log.warn("【小红书发布】素材删除被拒绝，路径越界：storageRef={}", storageRef);
                return;
            }
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("【小红书发布】本地素材删除失败：storageRef={}", storageRef, e);
        }
    }

    @Override
    public org.springframework.core.io.Resource loadAsResource(String storageRef) {
        if (!StringUtils.hasText(storageRef)) {
            throw new WorkException("素材路径不存在");
        }
        Path path = resolvePathWithinBase(storageRef, true);
        if (!Files.exists(path)) {
            throw new WorkException("素材文件不存在");
        }
        return new FileSystemResource(path);
    }

    private Path resolveBasePath() throws IOException {
        Path basePath = Paths.get(xhsPublishConfigRepository.getAssetBaseDir()).toAbsolutePath().normalize();
        Files.createDirectories(basePath);
        return basePath;
    }

    private Path resolvePathWithinBase(String storageRef, boolean throwOnOutOfBase) {
        try {
            Path basePath = resolveBasePath();
            Path targetPath = Paths.get(storageRef).toAbsolutePath().normalize();
            if (!targetPath.startsWith(basePath)) {
                if (throwOnOutOfBase) {
                    throw new WorkException("素材路径非法");
                }
                return null;
            }
            return targetPath;
        } catch (IOException e) {
            throw new WorkException("素材路径解析失败");
        }
    }

    private String normalizeBusinessPath(String businessPath) {
        if (!StringUtils.hasText(businessPath)) {
            return "default";
        }
        return businessPath.replace("..", "").replace('\\', '/');
    }

    private String resolveExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return ".bin";
        }
        String extension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        return extension.length() > 10 ? ".bin" : extension;
    }

}

