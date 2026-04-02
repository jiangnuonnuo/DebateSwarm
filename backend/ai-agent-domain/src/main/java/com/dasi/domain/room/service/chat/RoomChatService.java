package com.dasi.domain.room.service.chat;

import com.dasi.domain.room.apapter.port.IRoomEventPublisher;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.model.valobj.RoomChatRequest;
import com.dasi.domain.room.model.valobj.WebSocketEvent;
import com.dasi.domain.room.service.IContextAssemblerService;
import com.dasi.domain.room.service.IRoomChatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

import static com.dasi.domain.ai.model.enumeration.AiType.CLIENT;

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

    @Resource
    private ApplicationContext applicationContext;

    /**
     * 前端用户发送来的消息，对消息进行处理记录，然后发送回前端，并且通知agent进行消费
     * */
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
                .atMemberId(request.getSpecialMembers())
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
    public void clientChat(String roomId, String clientId) {
        log.info("【群聊服务】客户端响应触发 roomId={}, clientId={}", roomId, clientId);

        try {
            // 1. 获取客户端
            String beanName = CLIENT.getBeanName(clientId);
            ChatClient client = applicationContext.getBean(beanName, ChatClient.class);

            // 2. 准备基础信息
            String roomName = chatRoomRepository.queryRoomName(roomId);
            String clientName = chatRoomRepository.queryMemberName(roomId, clientId);

            // 3. 装配上下文
            List<Message> messages = contextAssemblerService.assemble(roomId, clientId, roomName, clientName);

            // 4. 同步调用 AI
            String content = client.prompt(new Prompt(messages))
                    .call()
                    .content();

            if (content == null || content.isEmpty()) {
                log.warn("【群聊服务】客户端回答为空 roomId={}, clientId={}", roomId, clientId);
                return;
            }

            // 5. 持久化结果
            String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            AiChatRoomMessageEntity aiMsg = AiChatRoomMessageEntity.builder()
                    .roomId(roomId)
                    .messageId(messageId)
                    .senderId(clientId)
                    .senderName(clientName)
                    .senderType("CLIENT")
                    .messageRole("assistant")
                    .content(content)
                    .isPreempted(1)
                    .build();

            contextAssemblerService.recordMessage(aiMsg);

            // 6. 发布事件 (对外广播 CLIENT_MSG + 对内触发信号 CLIENT_MSG_END)
            eventPublisher.publishExternal(WebSocketEvent.builder()
                    .roomId(roomId)
                    .eventType(WebSocketEvent.EventType.CLIENT_MSG)
                    .payload(aiMsg)
                    .timestamp(System.currentTimeMillis())
                    .build());

            eventPublisher.publishInternal(WebSocketEvent.builder()
                    .roomId(roomId)
                    .eventType(WebSocketEvent.EventType.CLIENT_MSG_END)
                    .payload(aiMsg)
                    .timestamp(System.currentTimeMillis())
                    .build());

            log.info("【群聊服务】客户端回答结束 roomId={}, clientId={}", roomId, clientId);

        } catch (Exception e) {
            log.error("【群聊服务】客户端执行失败 roomId={}, clientId={}", roomId, clientId, e);
        }
    }

    @Override
    public void publishSystemNotice(String roomId, String noticeType, String content, String extData) {
        AiChatRoomMessageEntity systemMsg = AiChatRoomMessageEntity.builder()
                .roomId(roomId)
                .messageId("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .senderId("system")
                .senderName("系统通知")
                .senderType("SYSTEM")
                .messageRole("system")
                .content(content)
                .extData(extData)
                .isPreempted(0)
                .build();

        contextAssemblerService.recordMessage(systemMsg);

        eventPublisher.publishExternal(WebSocketEvent.<AiChatRoomMessageEntity>builder()
                .roomId(roomId)
                .eventType(WebSocketEvent.EventType.SYSTEM_NOTICE)
                .payload(systemMsg)
                .timestamp(System.currentTimeMillis())
                .traceId(noticeType)
                .build());
    }
}
