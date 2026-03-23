package com.dasi.domain.room.service;

import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service
 * @Author: xerina
 * @CreateTime: 2026-03-23  11:03
 * @Description: 上下文装配器接口
 */
public interface IContextAssemblerService {

    /**
     * 为特定的 Agent 装配群聊上下文
     *
     * @param roomId            房间ID
     * @param agentId           智能体ID
     * @param roomName          房间名称
     * @param currentMemberName 当前智能体在房间的昵称
     * @return 组装后的消息列表
     */
    List<Message> assemble(String roomId, String agentId, String roomName, String currentMemberName);

    /**
     * 存储对话信息到上下文 (持久化)
     *
     * @param messageEntity 消息领域实体
     */
    void recordMessage(AiChatRoomMessageEntity messageEntity);

    /**
     * 为特定的 Agent 装配群聊上下文
     *
     * @param roomId      房间ID
     * @return 组装后的消息列表
     */
    String queryRoomExtConfig(String roomId );



}
