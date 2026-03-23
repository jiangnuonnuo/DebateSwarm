package com.dasi.domain.room.service.chat;

import com.dasi.domain.room.apapter.port.IRoomEventPublisher;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.model.valobj.AgentStreamPayload;
import com.dasi.domain.room.model.valobj.RoomChatRequest;
import com.dasi.domain.room.model.valobj.WebSocketEvent;
import com.dasi.domain.room.service.IContextAssemblerService;
import com.dasi.domain.room.service.IRoomChatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.domain.room.service.chat
 * @Author: xerina
 * @CreateTime: 2026-03-23  11:06
 * @Description: 聊天室对话服务实现类
 */
@Slf4j
@Service
public class RoomChatService implements IRoomChatService {

    @Resource
    private IChatRoomRepository chatRoomRepository;

    @Resource
    private IContextAssemblerService contextAssemblerService;

    @Resource
    private IRoomEventPublisher eventPublisher;

    @Resource(name = "SystemModel")
    private ChatModel chatModel;

    private final Random random = new Random();

    /** 响应概率 (领域逻辑：暂设 50%) */
    private static final double RESPONSE_PROBABILITY = 0.5;

    @Override
    public void onUserMessage(RoomChatRequest request) {
        String roomId = request.getRoomId();
        String userId = request.getUserId();
        String content = request.getContent();

        log.info("【群聊服务】处理用户消息 roomId={}, userId={}", roomId, userId);

        // 1. 获取发送者名称
        String senderName = chatRoomRepository.queryMemberName(roomId, userId);

        // 2. 构造消息实体并持久化
        AiChatRoomMessageEntity messageEntity = AiChatRoomMessageEntity.builder()
                .roomId(roomId)
                .messageId("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .senderId(userId)
                .senderName(senderName)
                .senderType("USER")
                .messageRole("user")
                .content(content)
                .isPreempted(0)
                .build();
        
        contextAssemblerService.recordMessage(messageEntity);

        // 3. 发布 WebSocket 事件 (对外广播 + 对内触发信号)
        WebSocketEvent<AiChatRoomMessageEntity> event = WebSocketEvent.<AiChatRoomMessageEntity>builder()
                .roomId(roomId)
                .eventType(WebSocketEvent.EventType.USER_MSG)
                .payload(messageEntity)
                .timestamp(System.currentTimeMillis())
                .traceId(request.getTraceId())
                .build();
        
        eventPublisher.publish(event);
    }

    @Override
    public void agentChat(String roomId, String agentId) {
        log.info("【群聊服务】智能体响应触发 roomId={}, agentId={}", roomId, agentId);

        // 1. 准备基础信息
        String roomName = chatRoomRepository.queryRoomName(roomId);
        String agentName = chatRoomRepository.queryMemberName(roomId, agentId);

        // 2. 装配上下文
        List<Message> messages = contextAssemblerService.assemble(roomId, agentId, roomName, agentName);

        // 3. 调用 AI (流式响应)
        StringBuilder fullContent = new StringBuilder();
        String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        Flux<ChatResponse> responseFlux = chatModel.stream(new Prompt(messages));

        responseFlux.doOnNext(chatResponse -> {
            // 兼容性修复：Spring AI 获取文本内容
            String fragment = chatResponse.getResult().getOutput().getText();
            if (fragment != null) {
                fullContent.append(fragment);
                // 推送流式片段给前端
                eventPublisher.publish(WebSocketEvent.builder()
                        .roomId(roomId)
                        .eventType(WebSocketEvent.EventType.AGENT_STREAM)
                        .payload(AgentStreamPayload.builder()
                                .agentId(agentId)
                                .agentName(agentName)
                                .content(fragment)
                                .isEnd(false)
                                .build())
                        .timestamp(System.currentTimeMillis())
                        .build());
            }
        }).doOnComplete(() -> {
            // 4. AI 回答结束，持久化结果
            AiChatRoomMessageEntity aiMsg = AiChatRoomMessageEntity.builder()
                    .roomId(roomId)
                    .messageId(messageId)
                    .senderId(agentId)
                    .senderName(agentName)
                    .senderType("AGENT")
                    .messageRole("assistant")
                    .content(fullContent.toString())
                    .isPreempted(1)
                    .build();
            
            contextAssemblerService.recordMessage(aiMsg);

            // 5. 发布结束信号 (这会再次触发内部监听器，形成 A2A 闭环)
            eventPublisher.publish(WebSocketEvent.builder()
                    .roomId(roomId)
                    .eventType(WebSocketEvent.EventType.AGENT_MSG_END)
                    .payload(aiMsg)
                    .timestamp(System.currentTimeMillis())
                    .build());
            
            log.info("【群聊服务】智能体回答结束 roomId={}, agentId={}", roomId, agentId);
        }).subscribe();
    }

    @Override
    public void dispatchNextSpeaker(WebSocketEvent<?> wsEvent) {
        String eventType = wsEvent.getEventType();
        String roomId = wsEvent.getRoomId();

        // 领域逻辑：只对“发言完成”信号感兴趣
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

        // 3. 概率性决策逻辑 (这是纯粹的领域规则)
        for (AiChatRoomMemberEntity agent : agents) {
            String agentId = agent.getMemberId();
            if (agentId.equals(currentSenderId)) continue;

            if (random.nextDouble() < RESPONSE_PROBABILITY) {
                log.info("【领域调度】命中概率响应：roomId={}, agentId={}", roomId, agentId);
                this.agentChat(roomId, agentId);
            }
        }
    }
}
