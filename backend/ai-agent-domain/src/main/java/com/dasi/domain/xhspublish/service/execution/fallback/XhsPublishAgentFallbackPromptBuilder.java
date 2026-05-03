package com.dasi.domain.xhspublish.service.execution.fallback;

import com.alibaba.fastjson2.JSON;
import com.dasi.domain.xhspublish.model.entity.XhsPublishContentAssetEntity;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class XhsPublishAgentFallbackPromptBuilder {

    private final String systemPrompt = loadPrompt("prompt/xhs/fallback-execute-system.txt");
    private final String userPromptTemplate = loadPrompt("prompt/xhs/fallback-execute-user.txt");

    public Prompt build(String taskId,
                        String attemptId,
                        String publishRequestJson,
                        Object sourceContext,
                        List<XhsPublishContentAssetEntity> assetList) {
        return new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPromptTemplate.formatted(
                        taskId,
                        attemptId,
                        publishRequestJson,
                        JSON.toJSONString(sourceContext),
                        JSON.toJSONString(buildAssetSummary(assetList))
                ))
        ));
    }

    private List<Map<String, Object>> buildAssetSummary(List<XhsPublishContentAssetEntity> assetList) {
        if (assetList == null || assetList.isEmpty()) {
            return List.of();
        }
        return assetList.stream()
                .filter(asset -> asset != null && StringUtils.hasText(asset.getAssetId()))
                .map(asset -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("assetId", asset.getAssetId());
                    item.put("storageType", asset.getStorageType());
                    item.put("storageRef", asset.getStorageRef());
                    item.put("accessUrl", asset.getAccessUrl());
                    item.put("originUrl", asset.getOriginUrl());
                    item.put("sortNo", asset.getSortNo());
                    return item;
                })
                .toList();
    }

    private String loadPrompt(String path) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载小红书发布兜底提示词失败: " + path, e);
        }
    }

}
