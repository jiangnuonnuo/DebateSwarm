package com.dasi.infrastructure.persistent.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Autor：xerina
 * @description：聊天室消息记录实体类
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiChatRoomMessage {
    /** 自增主键 */
    private Long id;

    /** 房间ID */
    private String roomId;

    /** 消息业务ID */
    private String messageId;

    /** 发送者ID(UserID或AgentID) */
    private String senderId;

    /** 发送者昵称(用于构建上下文) */
    private String senderName;

    /** 类型: USER, AGENT, SYSTEM */
    private String senderType;

    /** LLM角色: user, assistant, system */
    private String messageRole;

    /** 消息正文 */
    private String content;

    /** 被@的成员ID */
    private String atMemberId;

    /** 引用的消息ID */
    private String quoteMsgId;

    /** 是否抢占成功发言: 0-否, 1-是 */
    private Integer isPreempted;

    /** 扩展数据(JSON: 存储Token消耗、推理时长等) */
    private String extData;

    /** 伪删除标记: 0-正常, 1-已删除 */
    private Integer isDeleted;

    /** 发送时间 */
    private LocalDateTime createTime;
}
