package com.dasi.domain.room.service.chat;

import com.alibaba.fastjson2.JSON;
import com.dasi.domain.ai.service.dispatch.IDispatchService;
import com.dasi.domain.room.apapter.port.IRoomEventPublisher;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.model.valobj.ClientExecutionRequestVO;
import com.dasi.domain.room.model.valobj.ClientReplyResultVO;
import com.dasi.domain.room.model.valobj.RoomChatRequest;
import com.dasi.domain.room.model.valobj.WebSocketEvent;
import com.dasi.domain.room.service.IContextAssemblerService;
import com.dasi.domain.room.service.IRoomChatService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static com.dasi.domain.ai.model.enumeration.AiArmoryType.ARMORY_CHAT;
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

    @Resource
    private IDispatchService dispatchService;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Value("${room.debate.speaker-timeout-seconds:90}")
    private long debateSpeakerTimeoutSeconds;

    @Value("${room.chat.multi-at.timeout-seconds:90}")
    private long multiAtTimeoutSeconds;

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
    public void clientChat(ClientExecutionRequestVO request) {
        String roomId = request.getRoomId();
        String clientId = request.getClientId();
        String traceId = request.getDispatchTraceId();

        log.info("【CHAT_EXECUTE】准备执行辩手发言 roomId={}, clientId={}, traceId={}", roomId, clientId, traceId);
        ClientReplyResultVO result = doGenerateClientReply(request, debateSpeakerTimeoutSeconds, "CLIENT_CHAT");
        if (!result.isSuccess()) {
            if ("TIMEOUT".equals(result.getErrorType())) {
                log.warn("【CHAT_EXECUTE】客户端回答超时 roomId={}, clientId={}, traceId={}, timeoutSeconds={}",
                        roomId, clientId, traceId, debateSpeakerTimeoutSeconds);
            } else {
                log.error("【CHAT_EXECUTE】客户端执行失败 roomId={}, clientId={}, traceId={}, errorType={}, errorMessage={}",
                        roomId, clientId, traceId, result.getErrorType(), result.getErrorMessage());
            }
            publishClientExecutionError(request, result.getErrorType(), result.getErrorMessage());
            return;
        }

        AiChatRoomMessageEntity aiMsg = buildClientMessage(result, null);
        contextAssemblerService.recordMessage(aiMsg);

        eventPublisher.publishExternal(WebSocketEvent.builder()
                .roomId(roomId)
                .eventType(WebSocketEvent.EventType.CLIENT_MSG)
                .payload(aiMsg)
                .timestamp(System.currentTimeMillis())
                .traceId(traceId)
                .build());

        eventPublisher.publishInternal(WebSocketEvent.builder()
                .roomId(roomId)
                .eventType(WebSocketEvent.EventType.CLIENT_MSG_END)
                .payload(aiMsg)
                .timestamp(System.currentTimeMillis())
                .traceId(traceId)
                .build());

        log.info("【CHAT_EXECUTE】客户端回答结束 roomId={}, clientId={}, traceId={}, messageId={}",
                roomId, clientId, traceId, aiMsg.getMessageId());
    }

    @Override
    public ClientReplyResultVO generateClientReply(ClientExecutionRequestVO request) {
        return doGenerateClientReply(request, multiAtTimeoutSeconds, "MULTI_AT_CLIENT_CHAT");
    }

    @Override
    public void publishClientReplyResult(ClientReplyResultVO result) {
        if (result == null) {
            return;
        }

        if (!result.isSuccess()) {
            log.warn("【MULTI_AT_ITEM_FAILED】roomId={}, clientId={}, batchTraceId={}, orderIndex={}, errorType={}",
                    result.getRoomId(), result.getClientId(), result.getBatchTraceId(), result.getOrderIndex(), result.getErrorType());
            publishSystemNotice(
                    result.getRoomId(),
                    "MULTI_AT_ITEM_FAILED",
                    String.format("%s 本次未成功响应。", result.getClientName() == null || result.getClientName().isBlank() ? result.getClientId() : result.getClientName()),
                    buildMultiAtFailureExtData(result)
            );
            return;
        }

        AiChatRoomMessageEntity aiMsg = buildClientMessage(result, buildMultiAtSuccessExtData(result));
        contextAssemblerService.recordMessage(aiMsg);
        eventPublisher.publishExternal(WebSocketEvent.<AiChatRoomMessageEntity>builder()
                .roomId(result.getRoomId())
                .eventType(WebSocketEvent.EventType.CLIENT_MSG)
                .payload(aiMsg)
                .timestamp(System.currentTimeMillis())
                .traceId(result.getBatchTraceId())
                .build());

        log.info("【MULTI_AT_PUBLISH】roomId={}, clientId={}, batchTraceId={}, orderIndex={}, latencyMs={}",
                result.getRoomId(),
                result.getClientId(),
                result.getBatchTraceId(),
                result.getOrderIndex(),
                calculateLatency(result));
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

    private String invokeWithTimeout(Supplier<String> supplier, long timeoutSeconds, String stage) throws TimeoutException {
        try {
            return CompletableFuture.supplyAsync(supplier, threadPoolExecutor)
                    .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException timeoutException) {
                throw timeoutException;
            }
            throw new IllegalStateException("阶段执行失败: " + stage, cause == null ? e : cause);
        }
    }

    private void publishClientExecutionError(ClientExecutionRequestVO request, String errorType, String errorMessage) {
        String roomId = request.getRoomId();
        String clientId = request.getClientId();
        String traceId = request.getDispatchTraceId();
        String clientName = chatRoomRepository.queryMemberName(roomId, clientId);

        AiChatRoomMessageEntity errorPayload = AiChatRoomMessageEntity.builder()
                .roomId(roomId)
                .messageId("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .senderId(clientId)
                .senderName(clientName)
                .senderType("CLIENT")
                .messageRole("assistant")
                .content("")
                .extData(buildExecutionExtData(request, errorType, errorMessage))
                .isPreempted(1)
                .build();

        eventPublisher.publishInternal(WebSocketEvent.<AiChatRoomMessageEntity>builder()
                .roomId(roomId)
                .eventType(WebSocketEvent.EventType.CLIENT_MSG_ERROR)
                .payload(errorPayload)
                .timestamp(System.currentTimeMillis())
                .traceId(traceId) // 携带执行指纹
                .build());
    }

    private String buildExecutionExtData(ClientExecutionRequestVO request, String errorType, String errorMessage) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("dispatchTraceId", request.getDispatchTraceId());
        data.put("dispatchSessionId", request.getSessionId());
        data.put("dispatchRound", request.getRoundNumber());
        data.put("dispatchVersion", request.getSessionVersion());
        data.put("decisionSource", request.getDecisionSource());
        if (request.getReasoning() != null && !request.getReasoning().isBlank()) {
            data.put("decisionReasoning", request.getReasoning());
        }
        if (errorType != null) {
            data.put("errorType", errorType);
        }
        if (errorMessage != null) {
            data.put("errorMessage", errorMessage);
        }
        return JSON.toJSONString(data);
    }

    private String safeErrorMessage(Exception e) {
        if (e == null) {
            return "未知执行错误";
        }
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message;
    }

    private ClientReplyResultVO doGenerateClientReply(ClientExecutionRequestVO request, long timeoutSeconds, String stage) {
        String roomId = request.getRoomId();
        String clientId = request.getClientId();
        long startedAt = System.currentTimeMillis();
        String clientName = chatRoomRepository.queryMemberName(roomId, clientId);

        try {
            ChatClient client = ensureClientBean(clientId);
            if (client == null) {
                return buildFailureResult(request, clientName, "BEAN_MISSING", "目标 Client 尚未完成装配，无法执行本次发言。", startedAt);
            }

            String roomName = chatRoomRepository.queryRoomName(roomId);
            List<Message> messages = contextAssemblerService.assemble(roomId, clientId, roomName, clientName);
            String content = invokeWithTimeout(
                    () -> client.prompt(new Prompt(messages)).call().content(),
                    timeoutSeconds,
                    stage
            );

            if (content == null || content.isBlank()) {
                return buildFailureResult(request, clientName, "EMPTY_RESPONSE", "客户端未返回有效内容。", startedAt);
            }

            return ClientReplyResultVO.builder()
                    .roomId(roomId)
                    .clientId(clientId)
                    .clientName(clientName)
                    .content(content)
                    .success(true)
                    .batchTraceId(request.getBatchTraceId())
                    .orderIndex(request.getOrderIndex())
                    .requestedAtMemberIds(request.getRequestedAtMemberIds())
                    .startedAt(startedAt)
                    .finishedAt(System.currentTimeMillis())
                    .build();
        } catch (TimeoutException e) {
            return buildFailureResult(request, clientName, "TIMEOUT", "客户端调用超时。", startedAt);
        } catch (Exception e) {
            return buildFailureResult(request, clientName, "EXECUTION_FAILED", safeErrorMessage(e), startedAt);
        }
    }

    private ChatClient ensureClientBean(String clientId) {
        String beanName = CLIENT.getBeanName(clientId);
        if (!applicationContext.containsBean(beanName)) {
            dispatchService.dispatchArmoryStrategy(ARMORY_CHAT.getType(), java.util.Collections.singleton(clientId));
        }
        if (!applicationContext.containsBean(beanName)) {
            return null;
        }
        return applicationContext.getBean(beanName, ChatClient.class);
    }

    private ClientReplyResultVO buildFailureResult(ClientExecutionRequestVO request, String clientName, String errorType, String errorMessage, long startedAt) {
        return ClientReplyResultVO.builder()
                .roomId(request.getRoomId())
                .clientId(request.getClientId())
                .clientName(clientName)
                .success(false)
                .errorType(errorType)
                .errorMessage(errorMessage)
                .batchTraceId(request.getBatchTraceId())
                .orderIndex(request.getOrderIndex())
                .requestedAtMemberIds(request.getRequestedAtMemberIds())
                .startedAt(startedAt)
                .finishedAt(System.currentTimeMillis())
                .build();
    }

    private AiChatRoomMessageEntity buildClientMessage(ClientReplyResultVO result, String extData) {
        return AiChatRoomMessageEntity.builder()
                .roomId(result.getRoomId())
                .messageId("msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))
                .senderId(result.getClientId())
                .senderName(result.getClientName())
                .senderType("CLIENT")
                .messageRole("assistant")
                .content(result.getContent())
                .extData(extData)
                .isPreempted(1)
                .build();
    }

    private String buildMultiAtSuccessExtData(ClientReplyResultVO result) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("multiAt", true);
        data.put("batchTraceId", result.getBatchTraceId());
        data.put("orderIndex", result.getOrderIndex());
        data.put("requestedAtMemberIds", result.getRequestedAtMemberIds());
        data.put("startedAt", result.getStartedAt());
        data.put("finishedAt", result.getFinishedAt());
        return JSON.toJSONString(data);
    }

    private String buildMultiAtFailureExtData(ClientReplyResultVO result) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("noticeType", "MULTI_AT_ITEM_FAILED");
        data.put("multiAt", true);
        data.put("batchTraceId", result.getBatchTraceId());
        data.put("orderIndex", result.getOrderIndex());
        data.put("clientId", result.getClientId());
        data.put("clientName", result.getClientName());
        data.put("errorType", result.getErrorType());
        data.put("errorMessage", result.getErrorMessage());
        data.put("requestedAtMemberIds", result.getRequestedAtMemberIds());
        data.put("startedAt", result.getStartedAt());
        data.put("finishedAt", result.getFinishedAt());
        return JSON.toJSONString(data);
    }

    private long calculateLatency(ClientReplyResultVO result) {
        if (result.getStartedAt() == null || result.getFinishedAt() == null) {
            return -1L;
        }
        return Math.max(0L, result.getFinishedAt() - result.getStartedAt());
    }
}
