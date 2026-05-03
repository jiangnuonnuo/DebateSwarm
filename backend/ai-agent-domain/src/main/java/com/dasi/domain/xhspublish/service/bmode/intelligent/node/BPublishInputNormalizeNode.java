package com.dasi.domain.xhspublish.service.bmode.intelligent.node;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.xhspublish.service.bmode.context.BPublishIntelligentContext;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service("bPublishInputNormalizeNode")
public class BPublishInputNormalizeNode extends AbstractBPublishIntelligentNode {

    @Resource
    private BPublishDraftCreateNode draftCreateNode;

    @Override
    protected String doApply(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) throws Exception {
        // 输入归一化逻辑留在当前节点，节点自己对“能不能继续往下走”负责。
        List<MultipartFile> normalizedFiles = normalizeFiles(dynamicContext.getFileList());
        List<String> normalizedUrls = normalizeUrls(dynamicContext.getCommand().getOriginImageUrls());
        validateNormalizedInput(dynamicContext.getCommand().getPublishRequirement(), normalizedFiles, normalizedUrls);
        dynamicContext.setNormalizedFiles(normalizedFiles);
        dynamicContext.setNormalizedOriginImageUrls(normalizedUrls);
        log.info("【小红书发布】B智能预处理-输入归一化完成：fileCount={}, urlCount={}",
                dynamicContext.getNormalizedFiles().size(),
                dynamicContext.getNormalizedOriginImageUrls().size());
        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<BPublishIntelligentContext, BPublishIntelligentContext, String> get(BPublishIntelligentContext requestParameter, BPublishIntelligentContext dynamicContext) {
        return draftCreateNode;
    }

    private List<MultipartFile> normalizeFiles(List<MultipartFile> fileList) {
        if (fileList == null || fileList.isEmpty()) {
            return List.of();
        }
        return fileList.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
    }

    private List<String> normalizeUrls(List<String> originImageUrls) {
        if (originImageUrls == null || originImageUrls.isEmpty()) {
            return List.of();
        }
        return originImageUrls.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private void validateNormalizedInput(String publishRequirement,
                                         List<MultipartFile> normalizedFiles,
                                         List<String> normalizedUrls) {
        if (!StringUtils.hasText(publishRequirement)) {
            throw new WorkException("发布需求不能为空");
        }
        boolean hasFile = normalizedFiles != null && !normalizedFiles.isEmpty();
        boolean hasUrl = normalizedUrls != null && !normalizedUrls.isEmpty();
        if (!hasFile && !hasUrl) {
            throw new WorkException("图文发布至少需要 1 张图片，请上传文件或提供远程图片地址");
        }
    }

}
