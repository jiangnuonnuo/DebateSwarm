package com.dasi.domain.room.service.chat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.ai.service.dispatch.IDispatchService;
import com.dasi.domain.room.apapter.port.ISessionPort;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.model.valobj.RoomChatRequest;
import com.dasi.domain.room.model.valobj.WebSocketEvent;
import com.dasi.domain.room.service.IRoomChatService;
import com.dasi.domain.room.service.IRoomDispatchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.dasi.domain.ai.model.enumeration.AiArmoryType.ARMORY_CHAT;
import static com.dasi.domain.ai.model.enumeration.AiType.CLIENT;

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

    @Resource
    private IChatRoomRepository chatRoomRepository;

    @Resource
    private IDispatchService aiDispatchService;

    @Resource
    private ApplicationContext applicationContext;

    private final Random random = new Random();

    /** 响应概率 (领域逻辑：暂设 50%) */
    private static final double RESPONSE_PROBABILITY = 0.5;

    @Override
    public void onOpen(String roomId, String username, Object session) {
        log.info("【调度服务】接纳新连接：roomId={}, username={}", roomId, username);
        
        // 将 metadata 存入 Session 属性，方便后续消息识别
        if (session instanceof WebSocketSession) {
            WebSocketSession wsSession = (WebSocketSession) session;
            wsSession.getAttributes().put("roomId", roomId);
            wsSession.getAttributes().put("username", username);
        }
        
        sessionPort.addSession(roomId, username, session);
    }

    @Override
    public void onMessage(String roomId, String username, String payload) {
        log.debug("【调度服务】收到原始消息：roomId={}, username={}, payload={}", roomId, username, payload);
        try {
            JSONObject json = JSON.parseObject(payload);
            // 转化为领域对象请求
            RoomChatRequest request = RoomChatRequest.builder()
                    .roomId(roomId)
                    .userId(username)
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
    public void onClose(String roomId, String username) {
        log.info("【调度服务】清理连接：roomId={}, username={}", roomId, username);
        sessionPort.removeSession(roomId, username);
    }

    @Override
    public void dispatchNextSpeaker(WebSocketEvent<?> wsEvent) {
        String eventType = wsEvent.getEventType();
        String roomId = wsEvent.getRoomId();

        // 领域逻辑：只对“用户发言”或“智能体发言完成”信号感兴趣
        if (!WebSocketEvent.EventType.USER_MSG.equals(eventType)
                && !WebSocketEvent.EventType.AGENT_MSG_END.equals(eventType)) {
            return;
        }

        // 1. 识别当前发言者 ID
        String currentSenderId = "";
        if (wsEvent.getPayload() instanceof AiChatRoomMessageEntity) {
            currentSenderId = ((AiChatRoomMessageEntity) wsEvent.getPayload()).getSenderId();
        }

        // 2. 获取房间内候选 Agent
        List<AiChatRoomMemberEntity> agents = chatRoomRepository.queryAgentsByRoomId(roomId);
        if (agents == null || agents.isEmpty()) return;

        // 3. 概率性决策逻辑
        for (AiChatRoomMemberEntity agent : agents) {
            String agentId = agent.getMemberId();
            if (agentId.equals(currentSenderId)) continue;

            if (random.nextDouble() < RESPONSE_PROBABILITY) {
                log.info("【领域调度】命中概率响应：roomId={}, agentId={}", roomId, agentId);

                // 4. 资源检查与装配 (agentId 即 clientId)
                String beanName = CLIENT.getBeanName(agentId);
                if (!applicationContext.containsBean(beanName)) {
                    log.info("【领域调度】容器中不存在 Bean {} clientID {}，触发装配策略", beanName ,agentId);
                    aiDispatchService.dispatchArmoryStrategy(ARMORY_CHAT.getType(), Collections.singleton(agentId));
                }

                // 5. 下达执行指令
                roomChatService.agentChat(roomId, agentId);
            }
        }
    }
}
