package com.dasi.domain.xhspublish.service.support;

import com.dasi.domain.user.model.vo.QueryChatClientVO;
import com.dasi.domain.user.service.query.IQueryService;
import com.dasi.types.exception.WorkException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.dasi.domain.ai.model.enumeration.AiType.CLIENT;

@Slf4j
@Service
public class XhsPublishClientSupport {

    @Resource
    private IQueryService queryService;

    @Resource
    private ApplicationContext applicationContext;

    public void ensureClientAvailable(String clientId) {
        if (!StringUtils.hasText(clientId)) {
            throw new WorkException("发布生成 clientId 不能为空");
        }
        List<QueryChatClientVO> clientList = queryService.queryChatClientList();
        boolean exists = clientList != null && clientList.stream().anyMatch(item -> clientId.equals(item.getClientId()));
        if (!exists) {
            throw new WorkException("发布生成 client 不存在或未启用");
        }
        resolveClientBean(clientId);
    }

    public String promptOnce(String clientId, Prompt prompt) {
        ensureClientAvailable(clientId);
        try {
            String response = resolveClientBean(clientId).prompt(prompt).call().content();
            if (!StringUtils.hasText(response)) {
                throw new WorkException("智能生成结果为空");
            }
            return response.trim();
        } catch (WorkException e) {
            throw e;
        } catch (Exception e) {
            log.error("【小红书发布】智能生成失败：clientId={}", clientId, e);
            throw new WorkException("智能生成失败，请稍后重试");
        }
    }

    private ChatClient resolveClientBean(String clientId) {
        String beanName = CLIENT.getBeanName(clientId);
        try {
            return applicationContext.getBean(beanName, ChatClient.class);
        } catch (NoSuchBeanDefinitionException e) {
            throw new WorkException("发布生成 client 未装配，请先初始化客户端");
        }
    }

}
