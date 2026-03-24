package com.dasi.domain.room.service;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service
 * @Author: xerina
 * @CreateTime: 2026-03-23  23:01
 * @Description: 聊天室调度服务接口 (Trigger 与 Domain 的桥梁)
 */
public interface IRoomDispatchService {

    /**
     * 处理连接建立
     */
    void onOpen(String roomId, String username, Object session);

    /**
     * 处理收到的原始消息
     */
    void onMessage(String roomId, String username, String payload);

    /**
     * 处理连接关闭
     */
    void onClose(String roomId, String username);

    /**
     * 调度下一个发言者 (A2A 核心决策)
     *
     * @param event WebSocket 事件
     */
    void dispatchNextSpeaker(com.dasi.domain.room.model.valobj.WebSocketEvent<?> event);

}
