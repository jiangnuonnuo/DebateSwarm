package com.dasi.domain.xhspublish.service.generator;

import com.dasi.domain.xhspublish.model.dto.IntelligentXhsPublishSubmitDTO;
import com.dasi.domain.xhspublish.service.support.XhsPublishClientSupport;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class XhsPublishGeneratorService implements IXhsPublishGeneratorService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Resource
    private XhsPublishClientSupport clientSupport;

    @Resource
    private XhsPublishGeneratedContentParser generatedContentParser;

    private final String systemPrompt = loadPrompt("prompt/xhs/intelligent-submit-system.txt");
    private final String userPromptTemplate = loadPrompt("prompt/xhs/intelligent-submit-user.txt");

    @Override
    public GeneratedPublishContext generate(IntelligentXhsPublishSubmitDTO request, int imageCount) {
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(buildUserPrompt(request, imageCount))
        ));
        String rawResponse = clientSupport.promptOnce(request.getClientId(), prompt);
        return generatedContentParser.parse(rawResponse);
    }

    private String buildUserPrompt(IntelligentXhsPublishSubmitDTO request, int imageCount) {
        return userPromptTemplate.formatted(
                request.getTaskName().trim(),
                request.getPublishRequirement().trim(),
                imageCount,
                request.getVisibility() == null ? "未指定" : request.getVisibility().trim(),
                request.getIsOriginal() == null ? "未指定" : request.getIsOriginal(),
                request.getScheduledPublishAt() == null ? "未指定" : DATE_TIME_FORMATTER.format(request.getScheduledPublishAt())
        );
    }

    private String loadPrompt(String path) {
        try {
            return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载小红书发布提示词失败: " + path, e);
        }
    }

}
