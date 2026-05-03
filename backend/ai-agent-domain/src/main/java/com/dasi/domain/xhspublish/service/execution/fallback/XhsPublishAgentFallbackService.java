package com.dasi.domain.xhspublish.service.execution.fallback;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.dasi.domain.ai.model.entity.ArmoryRequestEntity;
import com.dasi.domain.ai.model.enumeration.AiArmoryType;
import com.dasi.domain.ai.service.armory.ArmoryContext;
import com.dasi.domain.ai.service.armory.ArmoryStrategyFactory;
import com.dasi.domain.ai.service.armory.IArmoryStrategy;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.xhspublish.model.entity.XhsPublishContentAssetEntity;
import com.dasi.domain.xhspublish.service.execution.remote.XhsPublishRemoteResult;
import com.dasi.types.exception.WorkException;
import io.modelcontextprotocol.client.McpSyncClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

import static com.dasi.domain.ai.model.enumeration.AiType.CLIENT;

@Slf4j
@Service
public class XhsPublishAgentFallbackService implements IXhsPublishAgentFallbackService {

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    private ArmoryStrategyFactory armoryStrategyFactory;

    @Resource
    private XhsPublishAgentFallbackPromptBuilder promptBuilder;

    @Resource
    private XhsPublishAgentFallbackResultParser resultParser;

    @Resource
    @Qualifier("xhsPublishMcpClient")
    private McpSyncClient xhsPublishMcpClient;

    @Override
    public XhsPublishRemoteResult execute(String clientId,
                                          String publishRequestJson,
                                          String taskId,
                                          String attemptId,
                                          JSONObject sourceContext,
                                          List<XhsPublishContentAssetEntity> assetList) {
        try {
            // 步骤 F1：调用方把本轮兜底真正需要的参数显式传入，fallback 不再依赖整包执行上下文。
            Prompt prompt = promptBuilder.build(taskId, attemptId, publishRequestJson, sourceContext, assetList);
            String rawResponse = promptOnceWithTools(
                    clientId,
                    prompt,
                    new SyncMcpToolCallbackProvider(xhsPublishMcpClient),
                    "Agent 发布兜底结果为空",
                    "Agent 发布兜底失败，请稍后重试"
            );

            return resultParser.parse(rawResponse);
        } catch (Exception e) {
            String message = sanitizeMessage(e.getMessage());
            log.warn("【小红书发布】Agent 兜底发布失败：taskId={}, attemptId={}, message={}",
                    taskId,
                    attemptId,
                    message);
            JSONObject result = new JSONObject();
            result.put("status", "failed");
            result.put("executor", "agent_fallback");
            result.put("error_code", "AGENT_FALLBACK_FAILED");
            result.put("error_type", "transient");
            result.put("error_message", message);
            return XhsPublishRemoteResult.builder()
                    .status("failed")
                    .resultJson(result.toJSONString())
                    .errorCode("AGENT_FALLBACK_FAILED")
                    .errorType("transient")
                    .errorMessage(message)
                    .executor("agent_fallback")
                    .build();
        }
    }

    private String promptOnceWithTools(String clientId,
                                       Prompt prompt,
                                       SyncMcpToolCallbackProvider toolCallbackProvider,
                                       String emptyMessage,
                                       String failureMessage) {
        ensureClientBeanReady(clientId);
        try {
            ChatClient chatClient = resolveClientBean(clientId);
            String response = chatClient.prompt(prompt).toolCallbacks(toolCallbackProvider).call().content();
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
        // fallback 只负责把生成 client 懒装配出来，不再重复做业务可用性判断。
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
            log.info("【小红书发布】fallback 检测到 client 未装配，开始懒装配：clientId={}", clientId);
            armoryStrategy.armory(armoryRequestEntity, armoryContext);
            armoryRootNode.apply(armoryRequestEntity, armoryContext);
            log.info("【小红书发布】fallback client 懒装配完成：clientId={}", clientId);
        } catch (Exception e) {
            log.error("【小红书发布】client 懒装配失败：clientId={}", clientId, e);
            throw new WorkException("发布生成 client 装配失败，请检查模型、提示词与工具配置");
        }
    }

    private String sanitizeMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "Agent 发布兜底失败";
        }
        String compact = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (compact.length() <= 240) {
            return compact;
        }
        return compact.substring(0, 240) + "...";
    }

}
