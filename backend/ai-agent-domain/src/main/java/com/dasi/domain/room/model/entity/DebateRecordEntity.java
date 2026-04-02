package com.dasi.domain.room.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @Author: xerina
 * @Description: 辩论发言记录领域实体
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DebateRecordEntity {
    /** 记录业务ID (dr_xxx) */
    private String recordId;

    /** 关联辩论会话ID */
    private String sessionId;

    /** 所属轮次(1-based) */
    private Integer roundNumber;

    /** 轮内对话序号(1-based) */
    private Integer turnNumber;

    /** 发言者CLIENT ID */
    private String speakerClientId;

    /** 发言者名称 (冗余字段) */
    private String speakerName;

    /** 阵营: PRO / CON */
    private String side;

    /** 关联消息ID */
    private String messageId;

    /** 仲裁者选择理由(限50字) */
    private String arbitratorReasoning;

    /** 创建时间 */
    private LocalDateTime createTime;
}
