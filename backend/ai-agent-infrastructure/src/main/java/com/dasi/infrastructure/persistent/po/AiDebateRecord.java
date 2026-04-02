package com.dasi.infrastructure.persistent.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Author: xerina
 * @Description: 辩论对话记录表持久化对象
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiDebateRecord {
    /** 自增主键 */
    private Long id;

    /** 记录业务ID (dr_xxx) */
    private String record_id;

    /** 关联辩论会话ID */
    private String sessionId;

    /** 所属轮次(1-based) */
    private Integer roundNumber;

    /** 轮内对话序号(1-based) */
    private Integer turnNumber;

    /** 发言者CLIENT ID */
    private String speakerClientId;

    /** 发言者名称 (冗余字段，优化查询性能) */
    private String speakerName;

    /** 阵营: PRO / CON */
    private String side;

    /** 关联 ai_chat_room_message.message_id */
    private String messageId;

    /** 仲裁者选择理由(限50字) */
    private String arbitratorReasoning;

    /** 消息内容 (JOIN 查询使用) */
    private String content;

    /** 创建时间 */
    private LocalDateTime createTime;
}
