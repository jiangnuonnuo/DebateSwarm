package com.dasi.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * @BelongsProject: Agent
 * @BelongsPackage: com.dasi.api.dto.response
 * @Author: xerina
 * @CreateTime: 2026-03-24  18:20
 * @Description: 聊天消息视图对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private String messageId;

    /** 发送者ID */
    private String senderId;

    /** 发送者名称 */
    private String senderName;

    /** 发送者类型: USER, AGENT */
    private String senderType;

    /** 消息角色: user, assistant */
    private String messageRole;

    /** 消息内容 */
    private String content;

    /** 是否抢占发言: 0-否, 1-是 */
    private Integer isPreempted;

    /** 发送时间 */
    private Date createTime;

    /** 游标时间戳 (Long) */
    private Long timestamp;

}
