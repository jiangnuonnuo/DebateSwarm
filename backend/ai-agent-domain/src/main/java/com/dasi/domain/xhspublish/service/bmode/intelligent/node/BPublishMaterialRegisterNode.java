package com.dasi.domain.xhspublish.service.bmode.intelligent.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.model.entity.XhsPublishContentAssetEntity;
import com.dasi.domain.xhspublish.model.entity.XhsPublishMaterialUploadCommandEntity;
import com.dasi.domain.xhspublish.service.bmode.context.BPublishIntelligentContext;
import com.dasi.domain.xhspublish.service.material.XhsPublishMaterialDomainService;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service("bPublishMaterialRegisterNode")
public class BPublishMaterialRegisterNode extends AbstractBPublishIntelligentNode {

    @Resource
    private XhsPublishMaterialDomainService materialDomainService;

    @Resource
    private BPublishCopyGenerateNode copyGenerateNode;

    @Override
    protected String doApply(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) throws Exception {

        List<String> materials = registerMaterials(dynamicContext.getTaskId(), dynamicContext.getNormalizedFiles(), dynamicContext.getNormalizedOriginImageUrls());
        dynamicContext.setSelectedAssetIds(materials);

        log.info("【小红书发布】B智能预处理-素材登记完成：taskId={}, assetCount={}",
                dynamicContext.getTaskId(), dynamicContext.getSelectedAssetIds().size());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<BPublishIntelligentContext, BPublishIntelligentContext, String> get(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) {
        return copyGenerateNode;
    }

    private List<String> registerMaterials(String taskId, List<MultipartFile> fileList, List<String> originImageUrls) {
        List<String> selectedAssetIds = new ArrayList<>();
        int sortNo = 1;
        for (MultipartFile file : fileList) {
            selectedAssetIds.add(saveSingleFile(taskId, file, sortNo++));
        }
        for (String originUrl : originImageUrls) {
            selectedAssetIds.add(saveSingleOriginUrl(taskId, originUrl, sortNo++));
        }
        return selectedAssetIds;
    }

    private String saveSingleFile(String taskId, MultipartFile file, int sortNo) {
        String sourceType = "intelligent_upload";
        List<MultipartFile> fileList = List.of(file);

        XhsPublishMaterialUploadCommandEntity command = XhsPublishMaterialUploadCommandEntity.builder()
                .taskId(taskId)
                .sourceType(sourceType)
                .sortNo(sortNo)
                .build();

        List<XhsPublishContentAssetEntity> assetList = materialDomainService.uploadMaterial(command, fileList);
        return requireAssetId(assetList);
    }

    private String saveSingleOriginUrl(String taskId, String originUrl, int sortNo) {
        String sourceType = "intelligent_origin_url";

        XhsPublishMaterialUploadCommandEntity command = XhsPublishMaterialUploadCommandEntity.builder()
                .taskId(taskId)
                .sourceType(sourceType)
                .originUrl(originUrl)
                .sortNo(sortNo)
                .build();

        List<XhsPublishContentAssetEntity> assetList = materialDomainService.uploadMaterial(command, null);
        return requireAssetId(assetList);
    }

    private String requireAssetId(List<XhsPublishContentAssetEntity> assetList) {
        if (assetList == null || assetList.isEmpty() || !StringUtils.hasText(assetList.get(0).getAssetId())) {
            throw new WorkException("素材登记失败");
        }
        return assetList.get(0).getAssetId();
    }

}
