package com.dasi.infrastructure.port.websocket;

import com.alibaba.fastjson2.JSON;
import com.dasi.domain.room.apapter.port.IRoomEventPublisher;
import com.dasi.domain.room.model.event.RoomMessageEvent;
import com.dasi.domain.room.model.valobj.WebSocketEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.infrastructure.port
 * @Author: xerina
 * @CreateTime: 2026-03-23  18:31
 * @Description: 聊天室事件发布器实现 (负责对外推送和对内发布)
 */
@Slf4j
@Service
public class RoomEventPublisher implements IRoomEventPublisher {

    @Resource
    private RoomSessionManager sessionManager;

    @Resource
    private ApplicationEventPublisher internalEventPublisher;

    @Override
    public void publish(WebSocketEvent<?> event) {
        String roomId = event.getRoomId();
        String jsonMsg = JSON.toJSONString(event);

        // 1. 对外推送 (WebSocket)
        Map<String, WebSocketSession> sessions = sessionManager.getSessionsByRoomId(roomId);
        if (!sessions.isEmpty()) {
            sessions.values().forEach(session -> {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(jsonMsg));
                    } catch (IOException e) {
                        log.error("【推送服务】消息推送失败 roomId={}, sessionId={}", roomId, session.getId(), e);
                    }
                }
            });
        }

        // 2. 对内发布 (Spring ApplicationEvent)
        internalEventPublisher.publishEvent(new RoomMessageEvent(this, event));
        
        log.debug("【推送服务】事件发布成功 roomId={}, type={}", roomId, event.getEventType());
    }
}
