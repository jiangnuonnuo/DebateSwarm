package com.dasi.domain.room.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Autor：xerina
 * @description：聊天室消息实体 (领域对象)
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiChatRoomMessageEntity {
    /** 房间ID */
    private String roomId;

    /** 消息业务ID */
    private String messageId;

    /** 发送者ID */
    private String senderId;

    /** 发送者昵称 */
    private String senderName;

    /** 类型: USER, AGENT, SYSTEM */
    private String senderType;

    /** LLM角色: user, assistant, system */
    private String messageRole;

    /** 消息正文 */
    private String content;

    /** 是否抢占成功发言 */
    private Integer isPreempted;

    /** 发送时间 */
    private LocalDateTime createTime;
}
