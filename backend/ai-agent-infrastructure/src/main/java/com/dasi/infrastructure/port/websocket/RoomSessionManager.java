package com.dasi.infrastructure.port.websocket;

import com.dasi.domain.room.apapter.port.ISessionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.infrastructure.port.websocket
 * @Author: xerina
 * @CreateTime: 2026-03-23  18:48
 * @Description: 聊天室 WebSocket 会话管理器 (线程安全)
 */
@Slf4j
@Component
public class RoomSessionManager implements ISessionPort {

    /**
     * 存储会话映射：roomId -> { userId -> WebSocketSession }
     */
    private final Map<String, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    /**
     * 添加会话
     */
    @Override
    public void addSession(String roomId, String userId, Object session) {
        if (!(session instanceof WebSocketSession)) {
            log.error("【会话管理】非法会话类型：{}", session != null ? session.getClass().getName() : "null");
            return;
        }
        WebSocketSession wsSession = (WebSocketSession) session;
        log.info("【会话管理】用户加入房间：roomId={}, userId={}, sessionId={}", roomId, userId, wsSession.getId());
        roomSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(userId, wsSession);
    }

    /**
     * 移除会话
     */
    @Override
    public void removeSession(String roomId, String userId) {
        log.info("【会话管理】用户退出房间：roomId={}, userId={}", roomId, userId);
        Map<String, WebSocketSession> userSessions = roomSessions.get(roomId);
        if (userSessions != null) {
            userSessions.remove(userId);
            if (userSessions.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }
    }

    /**
     * 根据房间ID获取所有在线会话
     */
    public Map<String, WebSocketSession> getSessionsByRoomId(String roomId) {
        return roomSessions.getOrDefault(roomId, Collections.emptyMap());
    }

    /**
     * 获取指定房间中特定用户的会话
     */
    public WebSocketSession getSession(String roomId, String userId) {
        Map<String, WebSocketSession> userSessions = roomSessions.get(roomId);
        return userSessions != null ? userSessions.get(userId) : null;
    }
}
