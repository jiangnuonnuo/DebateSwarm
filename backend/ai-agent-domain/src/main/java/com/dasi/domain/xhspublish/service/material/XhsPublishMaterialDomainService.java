package com.dasi.domain.xhspublish.service.material;

import com.dasi.domain.xhspublish.adapter.port.IXhsAssetStoragePort;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishConfigRepository;
import com.dasi.domain.xhspublish.adapter.repository.IXhsPublishRepository;
import com.dasi.domain.xhspublish.model.dto.UploadXhsPublishMaterialDTO;
import com.dasi.domain.xhspublish.model.entity.XhsPublishContentAssetEntity;
import com.dasi.domain.xhspublish.model.vo.XhsPublishAssetVO;
import com.dasi.domain.xhspublish.service.support.XhsPublishIdSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishTaskAccessSupport;
import com.dasi.domain.xhspublish.service.support.XhsPublishViewAssembler;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class XhsPublishMaterialDomainService {

    @Resource
    private IXhsPublishRepository publishRepository;

    @Resource
    private IXhsAssetStoragePort assetStoragePort;

    @Resource
    private XhsPublishIdSupport idSupport;

    @Resource
    private IXhsPublishConfigRepository xhsPublishConfigRepository;

    @Resource
    private XhsPublishTaskAccessSupport taskAccessSupport;

    @Resource
    private XhsPublishViewAssembler viewAssembler;

    @Transactional(rollbackFor = Exception.class)
    public List<XhsPublishAssetVO> uploadMaterial(UploadXhsPublishMaterialDTO dto, List<MultipartFile> fileList) {
        Long userId = taskAccessSupport.requireUserId();
        taskAccessSupport.queryOwnedTask(dto.getTaskId());

        List<XhsPublishAssetVO> result = new ArrayList<>();
        int sortNo = dto.getSortNo() == null ? 1 : dto.getSortNo();

        if (fileList != null && !fileList.isEmpty()) {
            for (MultipartFile file : fileList) {
                String storageRef = assetStoragePort.save(file, dto.getTaskId());
                String assetId = idSupport.nextAssetId();
                XhsPublishContentAssetEntity entity = XhsPublishContentAssetEntity.builder()
                        .assetId(assetId)
                        .taskId(dto.getTaskId())
                        .attemptId(dto.getAttemptId())
                        .userId(userId)
                        .assetType(dto.getAssetType())
                        .sourceType(dto.getSourceType())
                        .storageType("local")
                        .storageRef(storageRef)
                        .accessUrl(buildAssetAccessUrl(assetId))
                        .sortNo(sortNo++)
                        .assetStatus("ready")
                        .expireTime(LocalDateTime.now().plusHours(defaultHours(xhsPublishConfigRepository.getAssetRetentionHours())))
                        .build();
                publishRepository.saveAsset(entity);
                result.add(viewAssembler.toAssetVO(publishRepository.queryAssetByAssetId(assetId)));
            }
            return result;
        }

        if (!StringUtils.hasText(dto.getOriginUrl())) {
            throw new WorkException("请上传文件或提供 originUrl");
        }
        String assetId = idSupport.nextAssetId();
        XhsPublishContentAssetEntity entity = XhsPublishContentAssetEntity.builder()
                .assetId(assetId)
                .taskId(dto.getTaskId())
                .attemptId(dto.getAttemptId())
                .userId(userId)
                .assetType(dto.getAssetType())
                .sourceType(dto.getSourceType())
                .originUrl(dto.getOriginUrl())
                .storageType("url")
                .storageRef(dto.getOriginUrl())
                .accessUrl(dto.getOriginUrl())
                .sortNo(sortNo)
                .assetStatus("ready")
                .expireTime(LocalDateTime.now().plusHours(defaultHours(xhsPublishConfigRepository.getAssetRetentionHours())))
                .build();
        publishRepository.saveAsset(entity);
        result.add(viewAssembler.toAssetVO(publishRepository.queryAssetByAssetId(assetId)));
        return result;
    }

    public org.springframework.core.io.Resource accessMaterial(String assetId) {
        XhsPublishContentAssetEntity asset = publishRepository.queryAssetByAssetId(assetId);
        if (asset == null || !Objects.equals(asset.getUserId(), taskAccessSupport.requireUserId())) {
            throw new WorkException("素材不存在");
        }
        if (!"local".equalsIgnoreCase(asset.getStorageType())) {
            throw new WorkException("当前素材为远程地址，请直接使用 accessUrl");
        }
        return assetStoragePort.loadAsResource(asset.getStorageRef());
    }

    public void cleanupExpiredAssets() {
        List<XhsPublishContentAssetEntity> expiredList = publishRepository.listExpiredAsset(LocalDateTime.now());
        for (XhsPublishContentAssetEntity asset : expiredList) {
            if (asset == null || "deleted".equalsIgnoreCase(asset.getAssetStatus())) {
                continue;
            }
            if ("local".equalsIgnoreCase(asset.getStorageType())) {
                assetStoragePort.delete(asset.getStorageRef());
            }
            asset.setAssetStatus("deleted");
            publishRepository.saveAsset(asset);
        }
    }

    private String buildAssetAccessUrl(String assetId) {
        return xhsPublishConfigRepository.getAssetAccessBaseUrl() + "?assetId=" + assetId;
    }

    private int defaultHours(Integer hours) {
        return hours == null ? 24 : hours;
    }

}
