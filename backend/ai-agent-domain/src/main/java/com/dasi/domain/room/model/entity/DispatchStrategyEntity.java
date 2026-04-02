package com.dasi.domain.room.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * @Author: xerina
 * @Description: 调度策略实体 (领域层输入对象)
 * 职责：封装触发调度所需的领域信息，作为规则树的输入。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DispatchStrategyEntity {

    /** 房间ID */
    private String roomId;

    /** 触发事件类型 (USER_MSG / CLIENT_MSG_END) */
    private String eventType;

    /** 当前发言者ID */
    private String senderId;

    /** 被 @ 的成员ID集合 */
    private Set<String> atMemberIds;

    /** 触发当前调度的消息体 */
    private AiChatRoomMessageEntity currentMessage;

}
