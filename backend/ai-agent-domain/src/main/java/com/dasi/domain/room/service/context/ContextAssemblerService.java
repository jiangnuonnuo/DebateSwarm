package com.dasi.domain.room.service.context;

import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.service.IContextAssemblerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.context
 * @Author: xerina
 * @CreateTime: 2026-03-23  11:03
 * @Description: 上下文信息服务实现类 (纯净领域层实现)
 */
@Slf4j
@Service
public class ContextAssemblerService implements IContextAssemblerService {

    @Resource
    private IChatRoomRepository chatRoomRepository;

    /**
     * 上下文消息限制条数
     */
    private static final Integer CONTEXT_LIMIT = 20;

    @Override
    public List<Message> assemble(String roomId, String agentId, String roomName, String currentMemberName) {
        // 1. 直接从仓储层拉取最近的消息流 (返回的是 Entity 列表)
        List<AiChatRoomMessageEntity> messages = chatRoomRepository.queryContextMessages(roomId, CONTEXT_LIMIT);

        // 2. 核心逻辑：倒序取、正序拼
        Collections.reverse(messages);

        // 3. 构建提示词，todo:引入专业的提示词设计，存放文件内容于prompt支持场景特定化
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("你现在的名字是【").append(currentMemberName).append("】。")
                .append("你正在一个名为【").append(roomName).append("】的群聊房间中参与对话。\n")
                .append("请注意：在下文历史记录中看到标有 [").append(currentMemberName).append("] 的消息是你之前的发言。\n")
                .append("以下是该房间最近的 ").append(messages.size()).append(" 条对话记录：\n\n");

        // 4. 遍历组装平铺式对话流
        for (AiChatRoomMessageEntity msg : messages) {
            String senderName = msg.getSenderName();
            String content = msg.getContent();

            // 增强自我感知
            String displayName = senderName;
            if (msg.getSenderId().equals(agentId)) {
                displayName += " (你)";
            }

            contextBuilder.append("[").append(displayName).append("]: ").append(content).append("\n");
        }

        // 5. 最终封装
        log.info("【上下文装配】房间={}，Agent={}，装配条数={}", roomId, currentMemberName, messages.size());
        return List.of(new SystemMessage(contextBuilder.toString()));
    }
}
