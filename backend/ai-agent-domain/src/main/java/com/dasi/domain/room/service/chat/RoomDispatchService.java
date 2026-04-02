package com.dasi.domain.room.service.chat;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.domain.ai.service.dispatch.IDispatchService;
import com.dasi.domain.room.apapter.port.ISessionPort;
import com.dasi.domain.room.apapter.repository.IChatRoomRepository;
import com.dasi.domain.room.model.entity.AiChatRoomMemberEntity;
import com.dasi.domain.room.model.entity.AiChatRoomMessageEntity;
import com.dasi.domain.room.model.entity.DispatchStrategyEntity;
import com.dasi.domain.room.model.valobj.RoomChatRequest;
import com.dasi.domain.room.model.valobj.WebSocketEvent;
import com.dasi.domain.room.service.IRoomChatService;
import com.dasi.domain.room.service.IRoomDispatchService;
import com.dasi.domain.room.service.dispatch.DispatchStrategyFactory;
import com.dasi.types.constant.Constants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketSession;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private DispatchStrategyFactory dispatchStrategyFactory;

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

            String content = json.getString("content");
            // 直接从前端传来的 JSON 中获取逗号分隔的 memberId
            String specialMembers = json.getString("atMemberIds");
            log.info("被 @的人群有 {} ,房间id {}", specialMembers , roomId);
            // 转化为领域对象请求
            RoomChatRequest request = RoomChatRequest.builder()
                    .roomId(roomId)
                    .userId(username)
                    .content(content)
                    .specialMembers(specialMembers)
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

        // 领域逻辑：只对“用户发言”或“客户端发言完成”信号感兴趣
        if (!WebSocketEvent.EventType.USER_MSG.equals(eventType)
                && !WebSocketEvent.EventType.CLIENT_MSG_END.equals(eventType)) {
            return;
        }

        // 1. 识别当前发言者 ID 和 绑定的成员信息
        String currentSenderId = "";
        String atMemberId = "" ;
        if (wsEvent.getPayload() instanceof AiChatRoomMessageEntity) {
            currentSenderId = ((AiChatRoomMessageEntity) wsEvent.getPayload()).getSenderId();
            atMemberId = ((AiChatRoomMessageEntity) wsEvent.getPayload()).getAtMemberId();
        }

        // 2. 解析被 @ 的成员 ID 集合 (以逗号分隔)
        Set<String> atMemberIds = new HashSet<>();
        if (atMemberId != null && !atMemberId.trim().isEmpty()) {
            String[] ids = atMemberId.split(",");
            for (String id : ids) {
                if (!id.trim().isEmpty()) {
                    atMemberIds.add(id.trim());
                }
            }
        }

        // 3. 构建调度策略实体 (Domain Entity)
        DispatchStrategyEntity strategyEntity = DispatchStrategyEntity.builder()
                .roomId(roomId)
                .eventType(eventType)
                .senderId(currentSenderId)
                .atMemberIds(atMemberIds)
                .currentMessage(wsEvent.getPayload() instanceof AiChatRoomMessageEntity ? (AiChatRoomMessageEntity) wsEvent.getPayload() : null)
                .build();

        // 4. 调用决策树工厂执行调度逻辑
        dispatchStrategyFactory.doDispatch(strategyEntity);
    }


    /** 将名字按照 "张三,李四,王五"  拆成 列表，*/
    public List<String> extractNames(String membersStr) {
        if (membersStr == null || membersStr.isEmpty()) {
            return new ArrayList<>();
        }

        // 按逗号分割，并去除每个名字的前后空格
        return Arrays.stream(membersStr.split(Constants.commma))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toList());
    }
}
