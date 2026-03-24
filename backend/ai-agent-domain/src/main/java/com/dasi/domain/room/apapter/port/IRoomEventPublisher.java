package com.dasi.domain.room.apapter.port;

import com.dasi.domain.room.model.valobj.WebSocketEvent;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.apapter.port
 * @Author: xerina
 * @CreateTime: 2026-03-23  18:20
 * @Description: 聊天室事件发布接口 (领域层契约)
 */
public interface IRoomEventPublisher {

    /**
     * 发布聊天室事件 (同时推向前端和内部信号)
     * @param event 统一信封对象
     */
    void publish(WebSocketEvent<?> event);

    /**
     * 仅对外广播 (WebSocket)
     * @param event 统一信封对象
     */
    void publishExternal(WebSocketEvent<?> event);

    /**
     * 仅对内发布 (Spring ApplicationEvent)
     * @param event 统一信封对象
     */
    void publishInternal(WebSocketEvent<?> event);

}
