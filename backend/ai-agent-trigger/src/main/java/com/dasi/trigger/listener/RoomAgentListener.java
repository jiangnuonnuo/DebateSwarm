package com.dasi.trigger.listener;

import com.dasi.domain.room.model.event.RoomMessageEvent;
import com.dasi.domain.room.service.IRoomDispatchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.trigger.listener
 * @Author: xerina
 * @CreateTime: 2026-03-23  19:10
 * @Description: 聊天室内部消息监听器 (A2A 触发桥梁)
 */
@Slf4j
@Component
public class RoomAgentListener {

    @Resource
    private IRoomDispatchService roomDispatchService;

    /**
     * 监听内部消息事件
     * 职责：仅作为桥梁，监听到信号后转发给领域层做决策
     */
    @Async
    @EventListener
    public void onRoomMessage(RoomMessageEvent internalEvent) {
        log.info("【A2A 监听】监听到房间内部消息，转发领域层决策");
        roomDispatchService.dispatchNextSpeaker(internalEvent.getEvent());
    }
}
