package com.dasi.domain.room.service;

import com.dasi.domain.room.model.valobj.RoomChatRequest;
import com.dasi.domain.room.model.valobj.WebSocketEvent;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service
 * @Author: xerina
 * @CreateTime: 2026-03-23  11:06
 * @Description: 聊天室对话服务接口
 */
public interface IRoomChatService {

    /**
     * 处理用户上行消息
     */
    void onUserMessage(RoomChatRequest request);

    /**
     * 触发特定智能体发言
     * @param roomId 房间ID
     * @param agentId 智能体ID
     */
    void agentChat(String roomId, String agentId);

    /**
     * 决定下一个该谁说话 (A2A 决策逻辑)
     * @param event 当前收到的事件
     */
    void dispatchNextSpeaker(WebSocketEvent<?> event);

}
