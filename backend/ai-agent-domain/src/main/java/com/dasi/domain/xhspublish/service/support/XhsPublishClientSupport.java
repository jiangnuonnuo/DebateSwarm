package com.dasi.domain.xhspublish.service.support;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.ai.model.entity.ArmoryRequestEntity;
import com.dasi.domain.ai.model.enumeration.AiArmoryType;
import com.dasi.domain.ai.service.armory.ArmoryContext;
import com.dasi.domain.ai.service.armory.ArmoryStrategyFactory;
import com.dasi.domain.ai.service.armory.IArmoryStrategy;
import com.dasi.domain.user.model.vo.QueryChatClientVO;
import com.dasi.domain.user.service.query.IQueryService;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

import static com.dasi.domain.ai.model.enumeration.AiType.CLIENT;

@Slf4j
@Service
public class XhsPublishClientSupport {

    @Resource
    private IQueryService queryService;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private ArmoryStrategyFactory armoryStrategyFactory;

    public void ensureClientAvailable(String clientId) {
        // 步骤 1：先确认 clientId 在库里存在且处于可用状态，避免对无效 client 做运行时装配。
        if (!StringUtils.hasText(clientId)) {
            throw new WorkException("发布生成 clientId 不能为空");
        }
        List<QueryChatClientVO> clientList = queryService.queryChatClientList();
        boolean exists = clientList != null && clientList.stream().anyMatch(item -> clientId.equals(item.getClientId()));
        if (!exists) {
            throw new WorkException("发布生成 client 不存在或未启用");
        }

        // 步骤 2：如果 Spring 容器里还没有这个 ChatClient Bean，就在发布链路里懒装配一次。
        if (findClientBean(clientId) == null) {
            armoryClient(clientId);
        }

        // 步骤 3：装配完成后再次确认 Bean 可用；若仍然不存在，统一抛出明确异常。
        resolveClientBean(clientId);
    }

    public String promptOnce(String clientId, Prompt prompt) {
        return promptOnce(clientId, prompt, null, "智能生成结果为空", "智能生成失败，请稍后重试");
    }

    public String promptOnceWithTools(String clientId,
                                      Prompt prompt,
                                      SyncMcpToolCallbackProvider toolCallbackProvider,
                                      String emptyMessage,
                                      String failureMessage) {
        return promptOnce(clientId, prompt, toolCallbackProvider, emptyMessage, failureMessage);
    }

    private String promptOnce(String clientId,
                              Prompt prompt,
                              SyncMcpToolCallbackProvider toolCallbackProvider,
                              String emptyMessage,
                              String failureMessage) {
        ensureClientAvailable(clientId);
        try {
            var responseSpec = toolCallbackProvider == null
                    ? resolveClientBean(clientId).prompt(prompt).call()
                    : resolveClientBean(clientId).prompt(prompt).toolCallbacks(toolCallbackProvider).call();
            String response = responseSpec.content();
            if (!StringUtils.hasText(response)) {
                throw new WorkException(emptyMessage);
            }
            return response.trim();
        } catch (WorkException e) {
            throw e;
        } catch (Exception e) {
            log.error("【小红书发布】智能生成失败：clientId={}", clientId, e);
            throw new WorkException(failureMessage);
        }
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
            log.info("【小红书发布】检测到 client 未装配，开始懒装配：clientId={}", clientId);
            armoryStrategy.armory(armoryRequestEntity, armoryContext);
            armoryRootNode.apply(armoryRequestEntity, armoryContext);
            log.info("【小红书发布】client 懒装配完成：clientId={}", clientId);
        } catch (Exception e) {
            log.error("【小红书发布】client 懒装配失败：clientId={}", clientId, e);
            throw new WorkException("发布生成 client 装配失败，请检查模型、提示词与工具配置");
        }
    }

}
