package com.dasi.trigger.websocket;

import com.dasi.domain.room.service.IRoomDispatchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.trigger.websocket
 * @Author: xerina
 * @CreateTime: 2026-03-23  18:22
 * @Description: 聊天室 WebSocket 处理器 (收发外来信号)
 */
@Slf4j
@Component
public class ChatRoomSocketHandler extends TextWebSocketHandler {

    @Resource
    private IRoomDispatchService roomDispatchService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // roomId 和 username 已由 WebSocketConfig 中的 Interceptor 解析并放入 attributes
        Map<String, Object> attributes = session.getAttributes();
        String roomId = (String) attributes.get("roomId");
        String username = (String) attributes.get("username");

        if (roomId != null && username != null) {
            log.info("【WebSocket】连接已建立：roomId={}, username={}", roomId, username);
            roomDispatchService.onOpen(roomId, username, session);
        } else {
            log.warn("【WebSocket】连接缺少必要属性 roomId 或 username，即将关闭");
            try {
                session.close(CloseStatus.BAD_DATA);
            } catch (Exception e) {
                log.error("【WebSocket】关闭非法连接失败", e);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String roomId = (String) session.getAttributes().get("roomId");
        String username = (String) session.getAttributes().get("username");
        
        if (roomId != null && username != null) {
            roomDispatchService.onMessage(roomId, username, message.getPayload());
        } else {
            log.warn("【WebSocket】收到消息但 Session 属性缺失：sessionId={}", session.getId());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = (String) session.getAttributes().get("roomId");
        String username = (String) session.getAttributes().get("username");
        
        if (roomId != null && username != null) {
            log.info("【WebSocket】连接已关闭：roomId={}, username={}, status={}", roomId, username, status);
            roomDispatchService.onClose(roomId, username);
        }
    }
}
