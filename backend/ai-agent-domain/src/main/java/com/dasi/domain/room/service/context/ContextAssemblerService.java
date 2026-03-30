package com.dasi.domain.room.service.context;

import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.service.IContextAssemblerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.context
 * @Author: xerina
 * @CreateTime: 2026-03-23  11:03
 * @Description: 上下文信息服务实现类 (专业场景化装配版)
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

    /**
     * 专业场景指令模板 (精确描述，增强 Bot 的场景感知与交互逻辑)
     */
    private static final String SCENARIO_TEMPLATE =
            "# 群聊交互指令 (Group Chat Instructions)\n" +
            "---\n" +
            "## 1. 房间背景与定位 (Room Context)\n" +
            "- **房间名称**: 【%s】\n" +
            "- **房间描述**: %s\n\n" +
            "## 2. 你的身份与标识 (Your Identity)\n" +
            "- **展示名称**: 【%s】\n" +
            "- **唯一标识**: %s (memberId)\n" +
            "- **执行角色**: 请严格以上述身份，并基于下方的【对话历史】进行自然、连贯的回复。\n\n" +
            "## 3. 核心行为准则 (Behavioral Guidelines)\n" +
            "- **身份一致性**: 在历史记录中，标注为 [%s(%s) (你)] 的消息是你之前的发言，请确保回复逻辑的连贯性。\n" +
            "- **场景感知**: 你的回复风格应与上述【房间描述】高度契合。\n" +
            "- **社交敏感度**: 若对话中有人提及你的名字【%s】，请将其视为高优先级响应信号。\n" +
            "- **输出规范**: 直接输出回复内容，严禁在回复前添加任何形如 [名称(ID)]: 的前缀。\n\n" +
            "## 4. 对话历史 (Conversation History)\n" +
            "---";

    @Override
    public List<Message> assemble(String roomId, String agentId, String roomName, String currentMemberName) {
        // 1. 获取房间详情 (已接入缓存)
        AiChatRoomEntity roomEntity = chatRoomRepository.queryRoomById(roomId);
        String roomDesc = (roomEntity != null && roomEntity.getRoomDesc() != null) ? roomEntity.getRoomDesc() : "暂无背景描述";

        // 2. 从仓储层拉取最近的消息流 (DESC 排序)
        List<AiChatRoomMessageEntity> messages = chatRoomRepository.queryContextMessages(roomId, CONTEXT_LIMIT);

        // 3. 倒序处理，确保上下文是按时间正序排列 (从旧到新)
        Collections.reverse(messages);

        List<Message> messageList = new ArrayList<>();

        // 4. 构建专业场景指令消息 (UserMessage 注入)
        String scenarioContent = String.format(SCENARIO_TEMPLATE,
                roomName, roomDesc,
                currentMemberName, agentId,
                currentMemberName, agentId, currentMemberName);
        messageList.add(new UserMessage(scenarioContent));

        // 5. 遍历组装结构化的对话历史 (统一为 UserMessage，采用 [名称(ID)] 格式)
        for (AiChatRoomMessageEntity msg : messages) {
            String formattedContent;
            String senderName = msg.getSenderName();
            String senderId = msg.getSenderId();

            if (senderId.equals(agentId)) {
                // 标记“自我”发言
                formattedContent = String.format("[%s(%s) (你)]: %s", senderName, senderId, msg.getContent());
            } else {
                // 标记“他人”发言
                formattedContent = String.format("[%s(%s)]: %s", senderName, senderId, msg.getContent());
            }
            messageList.add(new UserMessage(formattedContent));
        }

        log.info("【上下文装配】房间={}，名称={}，ID={}，装配总条数={}", roomId, currentMemberName, agentId, messageList.size());
        return messageList;
    }

    @Override
    public void recordMessage(AiChatRoomMessageEntity messageEntity) {
        log.info("【消息持久化】房间={}，发送者={}，内容长度={}",
                messageEntity.getRoomId(), messageEntity.getSenderName(),
                messageEntity.getContent() != null ? messageEntity.getContent().length() : 0);
        chatRoomRepository.saveMessage(messageEntity);
    }

    public String queryRoomExtConfig(String roomId) {
        return chatRoomRepository.queryExtConfigByRoomId(roomId);
    }
}
