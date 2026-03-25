package com.dasi.domain.room.service;

import com.dasi.domain.room.model.valobj.RoomChatRequest;

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
     * 客户端发言 (由 DispatchService 调度)
     *
     * @param roomId  房间ID
     * @param clientId 客户端ID (clientId)
     */
    void clientChat(String roomId, String clientId);

}
