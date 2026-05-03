package com.dasi.domain.xhspublish.service.generator;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.ai.model.entity.ArmoryRequestEntity;
import com.dasi.domain.ai.model.enumeration.AiArmoryType;
import com.dasi.domain.ai.service.armory.ArmoryContext;
import com.dasi.domain.ai.service.armory.ArmoryStrategyFactory;
import com.dasi.domain.ai.service.armory.IArmoryStrategy;
import com.dasi.domain.xhspublish.model.entity.XhsPublishIntelligentSubmitCommandEntity;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static com.dasi.domain.ai.model.enumeration.AiType.CLIENT;

@Slf4j
@Service
public class XhsPublishGeneratorService implements IXhsPublishGeneratorService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private ArmoryStrategyFactory armoryStrategyFactory;

    @Resource
    private XhsPublishGeneratedContentParser generatedContentParser;

    private final String systemPrompt = loadPrompt("prompt/xhs/intelligent-submit-system.txt");
    private final String userPromptTemplate = loadPrompt("prompt/xhs/intelligent-submit-user.txt");

    @Override
    public GeneratedPublishContext generate(XhsPublishIntelligentSubmitCommandEntity request, int imageCount) {
        // 这里只做生成执行，不再重复做“client 是否属于当前用户”的业务校验。
        String clientId = request.getClientId();
        String userPrompt = buildUserPrompt(request, imageCount);
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userPrompt)
        ));
        String rawResponse = promptOnce(clientId, prompt);
        return generatedContentParser.parse(rawResponse);
    }

    private String buildUserPrompt(XhsPublishIntelligentSubmitCommandEntity request, int imageCount) {
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

    private String promptOnce(String clientId, Prompt prompt) {
        ensureClientBeanReady(clientId);
        try {
            ChatClient chatClient = resolveClientBean(clientId);
            String response = chatClient.prompt(prompt).call().content();
            if (!StringUtils.hasText(response)) {
                throw new WorkException("智能生成结果为空");
            }
            return response.trim();
        } catch (WorkException e) {
            throw e;
        } catch (Exception e) {
            throw new WorkException("智能生成失败，请稍后重试");
        }
    }

    private void ensureClientBeanReady(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            throw new WorkException("发布生成 clientId 不能为空");
        }
        ChatClient chatClient = findClientBean(clientId);
        if (chatClient == null) {
            armoryClient(clientId);
        }
        resolveClientBean(clientId);
    }

    private ChatClient resolveClientBean(String clientId) {
        ChatClient client = findClientBean(clientId);
        if (client != null) {
            return client;
        }
        throw new WorkException("发布生成 client 未装配，请先初始化客户端");
    }

    private ChatClient findClientBean(String clientId) {
        String beanName = CLIENT.getBeanName(clientId);
        try {
            return applicationContext.getBean(beanName, ChatClient.class);
        } catch (NoSuchBeanDefinitionException e) {
            return null;
        }
    }

    private void armoryClient(String clientId) {
        // 这里仅负责把 client Bean 懒装配到 Spring 容器，不承担业务可用性校验。
        String armoryType = AiArmoryType.ARMORY_CHAT.getType();
        IArmoryStrategy armoryStrategy = armoryStrategyFactory.getArmoryStrategyByType(armoryType);
        StrategyHandler<ArmoryRequestEntity, ArmoryContext, String> armoryRootNode = armoryStrategyFactory.getArmoryRootNode();
        if (armoryStrategy == null || armoryRootNode == null) {
            throw new WorkException("发布生成 client 装配能力不可用");
        }
        ArmoryRequestEntity armoryRequestEntity = ArmoryRequestEntity.builder()
                .armoryType(armoryType)
                .armoryIdSet(Set.of(clientId))
                .build();
        ArmoryContext armoryContext = new ArmoryContext();
        try {
            log.info("【小红书发布】生成服务检测到 client 未装配，开始懒装配：clientId={}", clientId);
            armoryStrategy.armory(armoryRequestEntity, armoryContext);
            armoryRootNode.apply(armoryRequestEntity, armoryContext);
            log.info("【小红书发布】生成服务 client 懒装配完成：clientId={}", clientId);
        } catch (Exception e) {
            log.error("【小红书发布】生成服务 client 懒装配失败：clientId={}", clientId, e);
            throw new WorkException("发布生成 client 装配失败，请检查模型、提示词与工具配置");
        }
    }

}
