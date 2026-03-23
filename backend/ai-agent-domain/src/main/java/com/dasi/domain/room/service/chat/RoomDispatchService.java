package com.dasi.domain.room.service.chat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.room.apapter.port.ISessionPort;
import com.dasi.domain.room.model.valobj.RoomChatRequest;
import com.dasi.domain.room.service.IRoomChatService;
import com.dasi.domain.room.service.IRoomDispatchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.chat
 * @Author: xerina
 * @CreateTime: 2026-03-23  23:15
 * @Description: 聊天室调度服务实现类
 */
@Slf4j
@Service
public class RoomDispatchService implements IRoomDispatchService {

    @Resource
    private ISessionPort sessionPort;

    @Resource
    private IRoomChatService roomChatService;

    @Override
    public void onOpen(String roomId, String userId, Object session) {
        log.info("【调度服务】接纳新连接：roomId={}, userId={}", roomId, userId);
        
        // 将 metadata 存入 Session 属性，方便后续消息识别
        if (session instanceof WebSocketSession) {
            WebSocketSession wsSession = (WebSocketSession) session;
            wsSession.getAttributes().put("roomId", roomId);
            wsSession.getAttributes().put("userId", userId);
        }
        
        sessionPort.addSession(roomId, userId, session);
    }

    @Override
    public void onMessage(String roomId, String userId, String payload) {
        log.debug("【调度服务】收到原始消息：roomId={}, userId={}, payload={}", roomId, userId, payload);
        try {
            JSONObject json = JSON.parseObject(payload);
            
            // 转化为领域对象请求
            RoomChatRequest request = RoomChatRequest.builder()
                    .roomId(roomId)
                    .userId(userId)
                    .content(json.getString("content"))
                    .traceId(json.getString("traceId"))
                    .build();

            // 调用核心对话服务
            roomChatService.onUserMessage(request);
            
        } catch (Exception e) {
            log.error("【调度服务】消息解析或处理失败：{}", payload, e);
        }
    }

    @Override
    public void onClose(String roomId, String userId) {
        log.info("【调度服务】清理连接：roomId={}, userId={}", roomId, userId);
        sessionPort.removeSession(roomId, userId);
    }
}
