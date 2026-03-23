package com.dasi.domain.room.model.event;

import com.dasi.domain.room.model.valobj.WebSocketEvent;
import org.springframework.context.ApplicationEvent;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.model.event
 * @Author: xerina
 * @CreateTime: 2026-03-23  18:45
 * @Description: 聊天室消息内部信号 (用于 A2A 闭环触发)
 */
public class RoomMessageEvent extends ApplicationEvent {

    private final WebSocketEvent<?> event;

    public RoomMessageEvent(Object source, WebSocketEvent<?> event) {
        super(source);
        this.event = event;
    }

    public WebSocketEvent<?> getEvent() {
        return event;
    }
}
